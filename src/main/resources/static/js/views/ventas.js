// Ventas.
//   POST /api/ventas                     (ADMIN y USER)  registrar
//   GET  /api/ventas                     (solo ADMIN)    historial
//   POST /api/ventas/{id}/cancelar       (solo ADMIN)    cancelar / devolver
//   GET  /api/productos                  para elegir productos
// Los subtotales y el total que se ven al armar la venta son una ESTIMACIÓN del navegador.
// El backend valida existencias y precios y calcula el total real; la pantalla muestra
// siempre lo que responde la API y no registra nada si la solicitud falla.
import * as api from '../api.js';
import {
  h, vaciar, tabla, dinero, fecha, entero, dialogo, confirmar, aviso, mensaje, ocupado, panelError, vacio, normalizar,
} from '../ui.js';

export async function render(raiz, yo) {
  const admin = yo.rol === 'ADMIN';
  let paginaHistorial = 0;
  let paginasHistorial = 0;
  let totalVentas = 0;
  const tamanoPaginaHistorial = 20;
  const panelNueva = h('div', {});
  const panelHistorial = h('div', { hidden: true });
  const btnNueva = h('button', { type: 'button', role: 'tab', 'aria-selected': 'true' }, 'Nueva venta');
  const btnHist = h('button', { type: 'button', role: 'tab', 'aria-selected': 'false' }, 'Historial de ventas');

  if (admin) {
    raiz.append(h('div', { class: 'pestanas', role: 'tablist' }, btnNueva, btnHist));
    const activar = (hist) => {
      btnNueva.setAttribute('aria-selected', String(!hist));
      btnHist.setAttribute('aria-selected', String(hist));
      panelNueva.hidden = hist;
      panelHistorial.hidden = !hist;
      if (hist) cargarHistorial();
    };
    btnNueva.addEventListener('click', () => activar(false));
    btnHist.addEventListener('click', () => activar(true));
  }
  raiz.append(panelNueva, panelHistorial);

  await nuevaVenta(panelNueva);

  // ---------- Historial ----------
  async function cargarHistorial() {
    vaciar(panelHistorial).append(h('p', { class: 'suave' }, 'Cargando ventas…'));
    try {
      const resultado = await api.get('/api/ventas/pagina', {
        pagina: paginaHistorial, tamano: tamanoPaginaHistorial,
      });
      if (paginaHistorial > 0 && paginaHistorial >= resultado.totalPages) {
        paginaHistorial = Math.max(0, resultado.totalPages - 1);
        return cargarHistorial();
      }
      const ventas = resultado.content;
      paginasHistorial = resultado.totalPages;
      totalVentas = resultado.totalElements;
      vaciar(panelHistorial).append(h('section', { class: 'seccion' },
        h('p', { class: 'seccion__cuerpo suave pequeno' },
          `${entero(totalVentas)} venta(s) en el historial. Mostrando ${entero(ventas.length)} en esta página.`),
        tabla([
          { titulo: 'Venta', principal: true, celda: (v) => `#${v.id}` },
          { titulo: 'Fecha', celda: (v) => fecha(v.fechaVenta) },
          { titulo: 'Registró', celda: (v) => v.usuario ?? '—' },
          { titulo: 'Líneas', num: true, celda: (v) => entero(v.detalles?.length ?? 0) },
          { titulo: 'Total', num: true, celda: (v) => dinero(v.total) },
          { titulo: 'Estado', celda: (v) => h('span', { class: `insignia ${v.estado === 'CANCELADA' ? 'insignia--error' : 'insignia--ok'}` }, v.estado === 'CANCELADA' ? 'Cancelada' : 'Activa') },
          {
            titulo: 'Acciones', acciones: true,
            celda: (v) => [
              h('button', { class: 'btn btn--texto', type: 'button', onclick: () => detalleVenta(v) }, 'Ver'),
              v.estado === 'CANCELADA' ? null
                : h('button', { class: 'btn btn--texto btn--peligro-texto', type: 'button', onclick: () => cancelar(v) }, 'Cancelar venta'),
            ],
          },
        ], ventas, { vacioTitulo: 'Aún no hay ventas', vacioDetalle: 'Las ventas que registres aparecerán aquí.' }),
        h('nav', { class: 'paginacion', 'aria-label': 'Paginación del historial de ventas' },
          h('button', { class: 'btn', type: 'button', disabled: paginaHistorial <= 0, onclick: () => cambiarPaginaHistorial(paginaHistorial - 1) }, 'Anterior'),
          h('span', { class: 'suave pequeno', 'aria-live': 'polite' }, paginasHistorial ? `Página ${paginaHistorial + 1} de ${paginasHistorial}` : 'Sin páginas'),
          h('button', { class: 'btn', type: 'button', disabled: paginasHistorial === 0 || paginaHistorial >= paginasHistorial - 1, onclick: () => cambiarPaginaHistorial(paginaHistorial + 1) }, 'Siguiente')));
    } catch (e) {
      vaciar(panelHistorial).append(panelError(e, cargarHistorial));
    }
  }

  function cambiarPaginaHistorial(nueva) {
    paginaHistorial = nueva;
    cargarHistorial();
  }

  async function cancelar(v) {
    const { ok, texto } = await confirmar({
      titulo: `Cancelar la venta #${v.id}`,
      mensaje: `Se devolverá al inventario el stock de ${v.detalles?.length ?? 0} línea(s) y la venta quedará marcada como cancelada. Esta acción no se puede deshacer.`,
      textoConfirmar: 'Cancelar venta', peligro: true,
      pedirTexto: { etiqueta: 'Motivo (opcional)', max: 500 },
    });
    if (!ok) return;
    try {
      await api.post(`/api/ventas/${v.id}/cancelar`, texto ? { motivo: texto } : {});
      aviso(`Venta #${v.id} cancelada y stock devuelto.`, 'ok');
      cargarHistorial();
    } catch (e) {
      aviso(e.message, 'error');
      cargarHistorial();
    }
  }
}

function detalleVenta(v) {
  const fila = (t, val) => [h('dt', {}, t), h('dd', {}, val ?? '—')];
  dialogo({
    titulo: `Venta #${v.id}`, ancho: true,
    cuerpo: [
      h('dl', { class: 'datos', style: 'margin-bottom:1rem' },
        fila('Fecha', fecha(v.fechaVenta)), fila('Registrada por', v.usuario), fila('Total', dinero(v.total)),
        fila('Estado', v.estado === 'CANCELADA' ? 'Cancelada' : 'Activa'),
        v.estado === 'CANCELADA' ? fila('Cancelada', `${fecha(v.fechaCancelacion)} por ${v.usuarioCancelacion ?? '—'}`) : null,
        v.motivoCancelacion ? fila('Motivo', v.motivoCancelacion) : null),
      tabla([
        { titulo: 'Producto', principal: true, celda: (d) => d.nombreProducto },
        { titulo: 'Cantidad', num: true, celda: (d) => entero(d.cantidad) },
        { titulo: 'Precio', num: true, celda: (d) => dinero(d.precioUnitario) },
        { titulo: 'Subtotal', num: true, celda: (d) => dinero(d.subtotal) },
      ], v.detalles || []),
    ],
    acciones: [h('button', { class: 'btn', type: 'button', onclick: (ev) => ev.target.closest('dialog').close() }, 'Cerrar')],
  });
}

async function nuevaVenta(raiz) {
  let productos = [];
  const carrito = new Map();            // productoId -> { p, cantidad }

  const buscar = h('input', { type: 'search', placeholder: 'Buscar producto por nombre', 'aria-label': 'Buscar producto', autocomplete: 'off' });
  const elegir = h('div', { class: 'lista-elegir', role: 'list' });
  const zonaCarrito = h('div', {});
  const zonaResultado = h('div', { 'aria-live': 'polite' });

  async function cargarProductos() {
    productos = await api.get('/api/productos');
    pintarLista();
    pintarCarrito();
  }

  const motivoBloqueo = (p) => {
    if (p.vencido === true || p.estado === 'Vencido' || p.vencidoPorFecha === true) return 'Vencido';
    if ((p.stock ?? 0) <= 0) return 'Agotado';
    return null;
  };

  function pintarLista() {
    const q = normalizar(buscar.value.trim());   // sin tildes ni mayúsculas: "cafe" encuentra "Café"
    const visibles = productos
      .filter((p) => p.activo !== false)
      .filter((p) => !q || [p.nombre, p.marca, p.categoria].some((t) => t && normalizar(t).includes(q)))
      .slice(0, 60);
    vaciar(elegir);
    if (!visibles.length) {
      elegir.append(vacio('Sin coincidencias', 'Prueba con otro nombre.'));
      return;
    }
    visibles.forEach((p) => {
      const bloqueo = motivoBloqueo(p);
      elegir.append(h('button', {
        type: 'button', role: 'listitem', disabled: !!bloqueo, onclick: () => agregar(p),
        'aria-label': `Agregar ${p.nombre}${bloqueo ? ` (${bloqueo})` : ''}`,
      },
      h('span', {}, p.nombre, h('span', { class: 'sub suave pequeno', style: 'display:block' }, bloqueo || `Disponibles: ${entero(p.stock)}`)),
      h('span', { class: 'num' }, dinero(p.precioVenta))));
    });
  }

  function agregar(p) {
    const actual = carrito.get(p.id);
    carrito.set(p.id, { p, cantidad: actual ? actual.cantidad + 1 : 1 });
    vaciar(zonaResultado);
    pintarCarrito();
  }

  function pintarCarrito() {
    vaciar(zonaCarrito);
    // refresca los datos del producto con el stock recién cargado
    carrito.forEach((linea, id) => {
      const fresco = productos.find((x) => x.id === id);
      if (fresco) linea.p = fresco;
    });
    if (!carrito.size) {
      zonaCarrito.append(vacio('La venta está vacía', 'Elige productos de la lista para agregarlos.'));
      return;
    }
    let estimado = 0;
    const lineas = [...carrito.values()].map(({ p, cantidad }) => {
      const sub = (p.precioVenta ?? 0) * cantidad;
      estimado += sub;
      const entrada = h('input', { type: 'number', min: 1, step: 1, value: cantidad, inputmode: 'numeric', 'aria-label': `Cantidad de ${p.nombre}` });
      const aviso = h('span', { class: 'pequeno', style: 'color:var(--ambar)', hidden: cantidad <= (p.stock ?? 0) },
        `Solo hay ${entero(p.stock)} disponibles.`);
      entrada.addEventListener('change', () => {
        const n = Math.floor(entrada.valueAsNumber);
        if (!Number.isFinite(n) || n < 1) { entrada.value = carrito.get(p.id).cantidad; return; }
        carrito.get(p.id).cantidad = n;
        pintarCarrito();
      });
      return h('div', { class: 'linea-venta' },
        h('div', {}, p.nombre, h('span', { class: 'sub suave pequeno', style: 'display:block' }, `${dinero(p.precioVenta)} c/u`), aviso),
        entrada,
        h('span', { class: 'num subtotal' }, dinero(sub)),
        h('button', { class: 'btn btn--icono btn--suave', type: 'button', 'aria-label': `Quitar ${p.nombre}`, onclick: () => { carrito.delete(p.id); pintarCarrito(); } }, '✕'));
    });
    const confirmarBtn = h('button', { class: 'btn btn--primario btn--ancho', type: 'button' }, 'Registrar venta');
    confirmarBtn.addEventListener('click', () => registrar(confirmarBtn));
    zonaCarrito.append(...lineas,
      h('div', { class: 'total-venta' }, h('span', {}, 'Total estimado'), h('span', { class: 'num' }, dinero(estimado))),
      h('p', { class: 'suave pequeno' }, 'Estimación en pantalla. El servidor confirma precios, existencias y el total real al registrar.'),
      confirmarBtn);
  }

  async function registrar(boton) {
    vaciar(zonaResultado);
    const cuerpo = { productos: [...carrito.values()].map(({ p, cantidad }) => ({ productoId: p.id, cantidad })) };
    await ocupado(boton, async () => {
      try {
        const venta = await api.post('/api/ventas', cuerpo);
        carrito.clear();
        zonaResultado.append(
          mensaje('ok', `Venta #${venta.id} registrada. Total confirmado por el servidor: ${dinero(venta.total)}.`),
          tabla([
            { titulo: 'Producto', principal: true, celda: (d) => d.nombreProducto },
            { titulo: 'Cantidad', num: true, celda: (d) => entero(d.cantidad) },
            { titulo: 'Subtotal', num: true, celda: (d) => dinero(d.subtotal) },
          ], venta.detalles || []));
        aviso(`Venta #${venta.id} registrada.`, 'ok');
        await cargarProductos();
      } catch (e) {
        const detalle = e.errores?.length ? ` ${e.errores.map((x) => x.mensaje).join(' ')}` : '';
        zonaResultado.append(mensaje('error', `No se registró la venta. ${e.message}${detalle}`));
        if (e.status === 409 || e.status === 404) cargarProductos().catch(() => {});   // el stock pudo cambiar
      }
    });
  }

  buscar.addEventListener('input', pintarLista);

  raiz.append(h('div', { class: 'venta' },
    h('section', { class: 'seccion' },
      h('div', { class: 'seccion__cabecera' }, h('h2', {}, 'Productos')),
      h('div', { class: 'seccion__cuerpo' }, buscar, h('div', { style: 'height:.75rem' }), elegir)),
    h('section', { class: 'seccion' },
      h('div', { class: 'seccion__cabecera' }, h('h2', {}, 'Venta actual')),
      h('div', { class: 'seccion__cuerpo' }, zonaCarrito, zonaResultado))));

  try { await cargarProductos(); } catch (e) { vaciar(elegir).append(panelError(e, cargarProductos)); }
}
