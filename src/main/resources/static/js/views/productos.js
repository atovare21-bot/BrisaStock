// Gestión de productos.
//   GET /api/productos, /api/productos/buscar?nombre=, /api/productos/{id}   (ADMIN y USER)
//   POST /api/productos, PUT /api/productos/{id}, DELETE /api/productos/{id} (solo ADMIN)
//   GET /api/inventario/productos/{id}/movimientos                           (solo ADMIN)
// El stock inicial solo se envía al crear. La edición (PUT) nunca envía stock:
// el stock cambia únicamente con las operaciones de Inventario.
import * as api from '../api.js';
import {
  h, vaciar, tabla, medidor, insigniaEstado, dinero, entero, campo, dialogo, confirmar, aviso,
  mensaje, ocupado, mostrarErrorCampo, limpiarErrores, panelError, debounce,
} from '../ui.js';
import { tablaMovimientos } from './inventario.js';

export async function render(raiz, yo) {
  const admin = yo.rol === 'ADMIN';
  let productos = [];
  let filtro = 'todos';
  let texto = '';

  const zona = h('div', { 'aria-live': 'polite' });
  const buscar = h('input', { type: 'search', placeholder: 'Buscar por nombre', 'aria-label': 'Buscar por nombre', maxlength: 100 });
  const selFiltro = h('select', { 'aria-label': 'Filtrar por estado' },
    h('option', { value: 'todos' }, 'Todos los productos'),
    h('option', { value: 'bajo' }, 'Con stock bajo'),
    h('option', { value: 'agotado' }, 'Agotados'),
    h('option', { value: 'vencido' }, 'Vencidos'),
    h('option', { value: 'inactivo' }, 'Desactivados'));
  const porId = h('input', { type: 'number', min: 1, step: 1, placeholder: 'ID', 'aria-label': 'ID del producto', style: 'width:5.6rem' });
  const btnId = h('button', { class: 'btn', type: 'button' }, 'Buscar por ID');

  const barra = h('div', { class: 'herramientas' },
    h('div', { class: 'crece' }, buscar), selFiltro, porId, btnId,
    admin ? h('button', { class: 'btn btn--primario', type: 'button', onclick: () => abrirFormulario(null, recargar) }, 'Nuevo producto') : null);
  raiz.append(barra, zona);

  const aplicaFiltro = (p) => {
    if (filtro === 'bajo') return p.stockBajo;
    if (filtro === 'agotado') return p.activo !== false && (p.stock ?? 0) <= 0;
    if (filtro === 'vencido') return p.activo !== false && (p.vencido === true || p.vencidoPorFecha === true);
    if (filtro === 'inactivo') return p.activo === false;
    return true;
  };

  function pintar() {
    const visibles = productos.filter(aplicaFiltro);
    vaciar(zona).append(h('section', { class: 'seccion' },
      tabla([
        { titulo: 'Producto', principal: true, celda: (p) => [p.nombre, h('span', { class: 'sub' }, [p.marca, p.categoria].filter(Boolean).join(' · '))] },
        { titulo: 'Precio de venta', num: true, celda: (p) => dinero(p.precioVenta) },
        { titulo: 'Stock', celda: (p) => medidor(p) },
        { titulo: 'Estado', celda: (p) => insigniaEstado(p) },
        {
          titulo: 'Acciones', acciones: true,
          celda: (p) => [
            h('button', { class: 'btn btn--texto', type: 'button', onclick: () => verDetalle(p.id, admin) }, 'Ver'),
            admin ? h('button', { class: 'btn btn--texto', type: 'button', onclick: () => abrirFormulario(p, recargar) }, 'Editar') : null,
            admin ? h('button', { class: 'btn btn--texto', type: 'button', onclick: () => cambiarActivo(p) }, p.activo === false ? 'Reactivar' : 'Desactivar') : null,
            admin ? h('button', { class: 'btn btn--texto btn--peligro-texto', type: 'button', onclick: () => eliminar(p) }, 'Eliminar') : null,
          ],
        },
      ], visibles, {
        vacioTitulo: texto || filtro !== 'todos' ? 'Ningún producto coincide' : 'Aún no hay productos',
        vacioDetalle: texto || filtro !== 'todos' ? 'Prueba con otro nombre o cambia el filtro.' : (admin ? 'Crea el primero con “Nuevo producto”.' : 'Un administrador debe registrarlos.'),
      }),
      h('p', { class: 'seccion__cuerpo suave pequeno' },
        `${entero(visibles.length)} de ${entero(productos.length)} productos. La barra del stock marca el mínimo de cada producto.`)));
  }

  async function recargar() {
    try {
      productos = texto ? await api.get('/api/productos/buscar', { nombre: texto }) : await api.get('/api/productos');
      pintar();
    } catch (e) {
      vaciar(zona).append(panelError(e, recargar));
    }
  }

  async function cambiarActivo(p) {
    const desactivar = p.activo !== false;
    if (desactivar) {
      const { ok } = await confirmar({
        titulo: 'Desactivar producto',
        mensaje: `¿Desactivar “${p.nombre}”? Ya no podrá venderse ni aparecerá en alertas, pero su historial de ventas y movimientos se conserva. Puede reactivarlo cuando quiera.`,
        textoConfirmar: 'Desactivar',
      });
      if (!ok) return;
    }
    try {
      await api.patch(`/api/productos/${p.id}/${desactivar ? 'desactivar' : 'activar'}`);
      aviso(`Producto “${p.nombre}” ${desactivar ? 'desactivado' : 'reactivado'}.`, 'ok');
      recargar();
    } catch (e) {
      aviso(e.message, 'error');
    }
  }

  async function eliminar(p) {
    const { ok } = await confirmar({
      titulo: 'Eliminar producto',
      mensaje: `¿Eliminar “${p.nombre}”? Esta acción no se puede deshacer. Si el producto tiene ventas o movimientos registrados, el sistema no lo permitirá.`,
      textoConfirmar: 'Eliminar producto', peligro: true,
    });
    if (!ok) return;
    try {
      await api.del(`/api/productos/${p.id}`);
      aviso(`Producto “${p.nombre}” eliminado.`, 'ok');
      recargar();
    } catch (e) {
      aviso(e.message, 'error');
    }
  }

  buscar.addEventListener('input', debounce(() => { texto = buscar.value.trim(); recargar(); }));
  selFiltro.addEventListener('change', () => { filtro = selFiltro.value; pintar(); });
  btnId.addEventListener('click', async () => {
    const id = porId.valueAsNumber;
    if (!Number.isInteger(id) || id < 1) { aviso('Escribe un ID de producto válido (número entero positivo).', 'aviso'); return; }
    verDetalle(id, admin);
  });
  porId.addEventListener('keydown', (e) => { if (e.key === 'Enter') btnId.click(); });

  await recargar();
}

/** Ficha de un producto. También la usa la búsqueda rápida (busqueda.js). */
export async function verDetalle(id, admin) {
  let p;
  try { p = await api.get(`/api/productos/${id}`); } catch (e) { aviso(e.message, 'error'); return; }
  const fila = (t, v) => [h('dt', {}, t), h('dd', {}, v ?? '—')];
  const cuerpo = [h('dl', { class: 'datos' },
    fila('ID', p.id), fila('Nombre', p.nombre), fila('Marca', p.marca), fila('Categoría', p.categoria),
    fila('Descripción', p.descripcion), fila('Precio de compra', dinero(p.precioCompra)),
    fila('Precio de venta', dinero(p.precioVenta)), fila('Stock', entero(p.stock)),
    fila('Stock mínimo', entero(p.stockMinimo ?? 0)), fila('Estado', insigniaEstado(p)), fila('Vencido por fecha', p.vencidoPorFecha ? 'Sí' : 'No'),
    fila('Fecha de vencimiento', p.fechaVencimiento))];
  if (admin) {
    const hueco = h('div', {}, h('p', { class: 'suave' }, 'Cargando movimientos…'));
    cuerpo.push(h('h3', { style: 'margin:1.25rem 0 .5rem' }, 'Últimos movimientos de inventario'), hueco);
    api.get(`/api/inventario/productos/${id}/movimientos`, { limite: 20 })
      .then((movs) => vaciar(hueco).append(tablaMovimientos(movs, { conProducto: false })))
      .catch((e) => vaciar(hueco).append(mensaje('error', e.message)));
  }
  dialogo({ titulo: p.nombre, cuerpo: cuerpo, ancho: admin, acciones: [h('button', { class: 'btn', type: 'button', onclick: (ev) => ev.target.closest('dialog').close() }, 'Cerrar')] });
}

function abrirFormulario(producto, alGuardar) {
  const nuevo = !producto;
  const p = producto || {};
  const nombre = h('input', { maxlength: 255, required: true, value: p.nombre ?? '' });
  const marca = h('input', { maxlength: 255, value: p.marca ?? '' });
  const categoria = h('input', { maxlength: 255, value: p.categoria ?? '' });
  const descripcion = h('input', { maxlength: 255, value: p.descripcion ?? '' });
  const precioCompra = h('input', { type: 'number', min: 0, step: 'any', inputmode: 'decimal', required: true, value: p.precioCompra ?? '' });
  const precioVenta = h('input', { type: 'number', min: 0, step: 'any', inputmode: 'decimal', required: true, value: p.precioVenta ?? '' });
  const stock = nuevo ? h('input', { type: 'number', min: 0, step: 1, inputmode: 'numeric', required: true, value: 0 }) : null;
  const stockMinimo = h('input', { type: 'number', min: 0, step: 1, inputmode: 'numeric', value: p.stockMinimo ?? '' });
  const vencido = h('input', { type: 'checkbox', checked: p.vencido === true });
  // fechaVencimiento es texto libre en la BD. Si el dato guardado no tiene formato AAAA-MM-DD,
  // un <input type="date"> lo mostraría vacío y al guardar se borraría; en ese caso se usa texto.
  const fechaIso = !p.fechaVencimiento || /^\d{4}-\d{2}-\d{2}$/.test(p.fechaVencimiento);
  const fechaVenc = h('input', fechaIso
    ? { type: 'date', value: p.fechaVencimiento ?? '' }
    : { type: 'text', maxlength: 255, value: p.fechaVencimiento });

  const errorGeneral = h('div', {});
  const form = h('form', { novalidate: true },
    errorGeneral,
    h('div', { class: 'rejilla' },
      h('div', { class: 'completo' }, campo('Nombre', nombre, { nombre: 'nombre' })),
      campo('Marca', marca, { nombre: 'marca' }),
      campo('Categoría', categoria, { nombre: 'categoria' }),
      h('div', { class: 'completo' }, campo('Descripción', descripcion, { nombre: 'descripcion' })),
      campo('Precio de compra', precioCompra, { nombre: 'precioCompra' }),
      campo('Precio de venta', precioVenta, { nombre: 'precioVenta' }),
      nuevo
        ? campo('Stock inicial', stock, { nombre: 'stock', ayuda: 'Queda registrado como una entrada en el historial.' })
        : h('div', { class: 'campo' }, h('span', { class: 'etiqueta' }, 'Stock actual'),
          h('p', { class: 'ayuda' }, `${entero(p.stock)} unidades. Para cambiarlo usa `, h('a', { href: '#/inventario', onclick: () => d.cerrar() }, 'Inventario'), ' (entrada, salida o ajuste).')),
      campo('Stock mínimo', stockMinimo, { nombre: 'stockMinimo', ayuda: 'El producto se marca con stock bajo cuando llega a este número o baja de él.' }),
      h('div', { class: 'campo' }, h('div', { class: 'casilla' }, vencido, h('label', { for: 'ch-vencido' }, 'Producto vencido'))),
      campo('Fecha de vencimiento', fechaVenc, { nombre: 'fechaVencimiento' })));
  vencido.id = 'ch-vencido';

  const guardar = h('button', { class: 'btn btn--primario', type: 'submit', form: 'form-producto' }, nuevo ? 'Crear producto' : 'Guardar cambios');
  form.id = 'form-producto';
  const cancelar = h('button', { class: 'btn', type: 'button', onclick: () => d.cerrar() }, 'Cancelar');

  form.addEventListener('submit', async (ev) => {
    ev.preventDefault();
    limpiarErrores(form);
    vaciar(errorGeneral);
    const errores = {};
    if (!nombre.value.trim()) errores.nombre = 'El nombre es obligatorio.';
    if (precioCompra.value === '' || precioCompra.valueAsNumber < 0) errores.precioCompra = 'Escribe un precio de compra de 0 o más.';
    if (precioVenta.value === '' || !(precioVenta.valueAsNumber > 0)) errores.precioVenta = 'El precio de venta debe ser mayor que cero.';
    if (nuevo && (stock.value === '' || !Number.isInteger(stock.valueAsNumber) || stock.valueAsNumber < 0)) errores.stock = 'El stock inicial debe ser un número entero de 0 o más.';
    if (stockMinimo.value !== '' && (!Number.isInteger(stockMinimo.valueAsNumber) || stockMinimo.valueAsNumber < 0)) errores.stockMinimo = 'El stock mínimo debe ser un número entero de 0 o más.';
    if (Object.keys(errores).length) {
      Object.entries(errores).forEach(([k, m]) => mostrarErrorCampo(form, k, m));
      form.querySelector('.campo.invalido input')?.focus();
      return;
    }
    const cuerpo = {
      nombre: nombre.value.trim(), marca: marca.value.trim(), categoria: categoria.value.trim(),
      descripcion: descripcion.value.trim(), precioCompra: precioCompra.valueAsNumber,
      precioVenta: precioVenta.valueAsNumber,
      stockMinimo: stockMinimo.value === '' ? null : stockMinimo.valueAsNumber,
      vencido: vencido.checked, fechaVencimiento: fechaVenc.value.trim(),
    };
    if (nuevo) cuerpo.stock = stock.valueAsNumber;      // PUT nunca envía stock
    await ocupado(guardar, async () => {
      try {
        if (nuevo) await api.post('/api/productos', cuerpo);
        else await api.put(`/api/productos/${p.id}`, cuerpo);
        aviso(nuevo ? 'Producto creado.' : 'Cambios guardados.', 'ok');
        d.cerrar();
        alGuardar();
      } catch (e) {
        const sinCampo = [];
        (e.errores || []).forEach((x) => { if (!mostrarErrorCampo(form, x.campo, x.mensaje)) sinCampo.push(x); });
        errorGeneral.append(mensaje('error', e.errores?.length ? 'Revisa los campos marcados.' : e.message));
        sinCampo.forEach((x) => errorGeneral.append(mensaje('error', `${x.campo}: ${x.mensaje}`)));
      }
    });
  });

  const d = dialogo({ titulo: nuevo ? 'Nuevo producto' : `Editar ${p.nombre}`, cuerpo: form, acciones: [cancelar, guardar] });
  nombre.focus();
}
