// Inventario y movimientos.
//   POST /api/inventario/entradas   (ADMIN y USER)
//   POST /api/inventario/salidas    (solo ADMIN)
//   POST /api/inventario/ajustes    (solo ADMIN)
//   GET  /api/inventario/movimientos y /productos/{id}/movimientos (solo ADMIN)
//   GET  /api/productos             (para elegir el producto)
// Los movimientos no se pueden editar ni borrar: el backend no ofrece esa operación.
import * as api from '../api.js';
import {
  h, vaciar, tabla, campo, confirmar, aviso, mensaje, ocupado, mostrarErrorCampo, limpiarErrores,
  insigniaTipo, fecha, entero, panelError, vacio,
} from '../ui.js';

/** Tabla de movimientos reutilizada por Productos (detalle) e Inventario (historial). */
export function tablaMovimientos(movs, { conProducto = true } = {}) {
  const columnas = [
    { titulo: 'Fecha', principal: true, celda: (m) => fecha(m.fecha) },
    conProducto ? { titulo: 'Producto', celda: (m) => m.productoNombre } : null,
    { titulo: 'Tipo', celda: (m) => insigniaTipo(m.tipo) },
    { titulo: 'Cantidad', num: true, celda: (m) => entero(m.cantidad) },
    { titulo: 'Stock', num: true, celda: (m) => `${entero(m.stockAnterior)} → ${entero(m.stockPosterior)}` },
    { titulo: 'Responsable', celda: (m) => m.usuario },
    {
      titulo: 'Detalle',
      celda: (m) => [m.referencia, m.observacion ? h('span', { class: 'sub' }, m.observacion) : null],
    },
  ].filter(Boolean);
  return tabla(columnas, movs, { vacioTitulo: 'Sin movimientos', vacioDetalle: 'Todavía no hay cambios de stock registrados.' });
}

const TEXTO_OPERACION = { entrada: 'Entrada', salida: 'Salida', ajuste: 'Ajuste' };

export async function render(raiz, yo) {
  const admin = yo.rol === 'ADMIN';
  let productos = await api.get('/api/productos');
  const lista = h('datalist', { id: 'lista-productos' });
  const llenarLista = () => {
    vaciar(lista).append(...productos.map((p) => h('option', { value: `${p.id} · ${p.nombre}` })));
  };
  llenarLista();

  const buscarProducto = (texto) => {
    const m = /^\s*(\d+)/.exec(texto || '');
    if (!m) return null;
    return productos.find((p) => p.id === Number(m[1])) || null;
  };

  // ---------- Formulario ----------
  const tipo = h('select', {}, h('option', { value: 'entrada' }, 'Entrada: sumar stock'),
    admin ? h('option', { value: 'salida' }, 'Salida: restar stock') : null,
    admin ? h('option', { value: 'ajuste' }, 'Ajuste: fijar el stock a un valor') : null);
  const producto = h('input', { list: 'lista-productos', placeholder: 'Escribe el nombre o el ID', autocomplete: 'off', required: true });
  const cantidad = h('input', { type: 'number', min: 1, step: 1, inputmode: 'numeric' });
  const referencia = h('input', { maxlength: 120, placeholder: 'Opcional: factura, proveedor…' });
  const observacion = h('textarea', { maxlength: 500 });
  const infoStock = h('p', { class: 'ayuda', style: 'margin:.2rem 0 0' });
  const resultado = h('div', { 'aria-live': 'polite' });
  const errorGeneral = h('div', {});

  const campoTipo = campo('Operación', tipo, { nombre: 'tipo' });
  const campoProducto = campo('Producto', producto, { nombre: 'productoId' });
  campoProducto.querySelector('.error').before(infoStock);
  const campoCantidad = campo('Cantidad', cantidad, { nombre: 'cantidad' });
  const campoReferencia = campo('Referencia', referencia, { nombre: 'referencia' });
  const campoObs = campo('Observación', observacion, { nombre: 'observacion' });
  const etiquetaCantidad = campoCantidad.querySelector('label');
  const etiquetaObs = campoObs.querySelector('label');

  function ajustarFormulario() {
    const op = tipo.value;
    const esAjuste = op === 'ajuste';
    etiquetaCantidad.textContent = esAjuste ? 'Nuevo stock' : 'Cantidad';
    cantidad.min = esAjuste ? 0 : 1;
    campoCantidad.dataset.campo = esAjuste ? 'nuevoStock' : 'cantidad';
    campoReferencia.hidden = esAjuste;
    etiquetaObs.textContent = esAjuste ? 'Motivo del ajuste (obligatorio)' : 'Observación';
    limpiarErrores(form);
  }
  function mostrarStock() {
    const p = buscarProducto(producto.value);
    infoStock.textContent = p ? `Stock actual: ${entero(p.stock)} · mínimo ${entero(p.stockMinimo ?? 0)}` : '';
  }

  const enviar = h('button', { class: 'btn btn--primario', type: 'submit' }, 'Registrar movimiento');
  const form = h('form', { novalidate: true }, errorGeneral, campoTipo, campoProducto, campoCantidad, campoReferencia, campoObs, enviar);
  tipo.addEventListener('change', ajustarFormulario);
  producto.addEventListener('input', mostrarStock);

  form.addEventListener('submit', async (ev) => {
    ev.preventDefault();
    limpiarErrores(form);
    vaciar(errorGeneral);
    vaciar(resultado);
    const op = tipo.value;
    const p = buscarProducto(producto.value);
    const errores = [];
    if (!p) errores.push(['productoId', 'Elige un producto de la lista.']);
    const valor = cantidad.valueAsNumber;
    const campoValor = op === 'ajuste' ? 'nuevoStock' : 'cantidad';
    if (cantidad.value === '' || !Number.isInteger(valor)) errores.push([campoValor, 'Escribe un número entero.']);
    else if (op === 'ajuste' && valor < 0) errores.push([campoValor, 'El stock no puede ser negativo.']);
    else if (op !== 'ajuste' && valor < 1) errores.push([campoValor, 'La cantidad debe ser mayor que cero.']);
    if (op === 'ajuste' && !observacion.value.trim()) errores.push(['observacion', 'Todo ajuste debe indicar el motivo.']);
    if (errores.length) {
      errores.forEach(([k, m]) => mostrarErrorCampo(form, k, m));
      return;
    }

    // Operaciones delicadas: confirmación antes de enviar
    if (op !== 'entrada') {
      const texto = op === 'salida'
        ? `Vas a restar ${entero(valor)} unidades de “${p.nombre}” (stock actual: ${entero(p.stock)}).`
        : `Vas a fijar el stock de “${p.nombre}” en ${entero(valor)} (stock actual: ${entero(p.stock)}).`;
      const { ok } = await confirmar({ titulo: `Confirmar ${TEXTO_OPERACION[op].toLowerCase()}`, mensaje: `${texto} Quedará registrado a tu nombre.`, textoConfirmar: `Registrar ${TEXTO_OPERACION[op].toLowerCase()}` });
      if (!ok) return;
    }

    const cuerpo = op === 'ajuste'
      ? { productoId: p.id, nuevoStock: valor, observacion: observacion.value.trim() }
      : { productoId: p.id, cantidad: valor, referencia: referencia.value.trim() || null, observacion: observacion.value.trim() || null };

    await ocupado(enviar, async () => {
      try {
        const mov = await api.post(`/api/inventario/${op === 'ajuste' ? 'ajustes' : op === 'salida' ? 'salidas' : 'entradas'}`, cuerpo);
        resultado.append(mensaje('ok', `${TEXTO_OPERACION[op]} registrada: el stock de “${mov.productoNombre}” pasó de ${entero(mov.stockAnterior)} a ${entero(mov.stockPosterior)}.`));
        aviso(`${TEXTO_OPERACION[op]} registrada.`, 'ok');
        cantidad.value = ''; referencia.value = ''; observacion.value = '';
        productos = await api.get('/api/productos');       // refleja el stock real
        llenarLista();
        mostrarStock();
        if (admin) recargarHistorial();
      } catch (e) {
        const sinCampo = [];
        (e.errores || []).forEach((x) => { if (!mostrarErrorCampo(form, x.campo, x.mensaje)) sinCampo.push(x); });
        errorGeneral.append(mensaje('error', e.errores?.length ? 'Revisa los campos marcados.' : e.message));
        sinCampo.forEach((x) => errorGeneral.append(mensaje('error', `${x.campo}: ${x.mensaje}`)));
      }
    });
  });

  raiz.append(
    lista,
    h('section', { class: 'seccion', style: 'max-width:640px' },
      h('div', { class: 'seccion__cabecera' }, h('h2', {}, 'Registrar movimiento')),
      h('div', { class: 'seccion__cuerpo' },
        admin ? null : mensaje('info', 'Tu rol puede registrar entradas. Las salidas, los ajustes y el historial los gestiona un administrador.'),
        form, resultado)));
  ajustarFormulario();

  // ---------- Historial (solo ADMIN) ----------
  let recargarHistorial = () => {};
  if (admin) {
    const filtro = h('input', { list: 'lista-productos', placeholder: 'Todos los productos', 'aria-label': 'Filtrar por producto', autocomplete: 'off' });
    const limite = h('select', { 'aria-label': 'Cantidad de movimientos' },
      h('option', { value: 50 }, 'Últimos 50'), h('option', { value: 200, selected: true }, 'Últimos 200'), h('option', { value: 1000 }, 'Últimos 1000'));
    const zona = h('div', { 'aria-live': 'polite' });
    recargarHistorial = async () => {
      const p = filtro.value.trim() ? buscarProducto(filtro.value) : null;
      if (filtro.value.trim() && !p) {
        vaciar(zona).append(mensaje('aviso', 'Elige un producto de la lista o deja el filtro vacío.'));
        return;
      }
      try {
        const movs = p
          ? await api.get(`/api/inventario/productos/${p.id}/movimientos`, { limite: limite.value })
          : await api.get('/api/inventario/movimientos', { limite: limite.value });
        vaciar(zona).append(tablaMovimientos(movs, { conProducto: !p }));
      } catch (e) {
        vaciar(zona).append(panelError(e, recargarHistorial));
      }
    };
    filtro.addEventListener('change', recargarHistorial);
    limite.addEventListener('change', recargarHistorial);
    raiz.append(h('section', { class: 'seccion' },
      h('div', { class: 'seccion__cabecera' }, h('h2', {}, 'Historial de movimientos'),
        h('div', { class: 'herramientas', style: 'margin:0' }, filtro, limite,
          h('button', { class: 'btn', type: 'button', onclick: recargarHistorial }, 'Actualizar'))),
      zona));
    await recargarHistorial();
  }
}
