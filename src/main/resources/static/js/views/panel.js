// Panel: indicadores calculados con los datos reales de la API.
//   GET /api/productos        (ADMIN y USER) -> total, stock bajo, agotados, vencidos
//   GET /api/ventas           (solo ADMIN)   -> últimas ventas
// "Stock bajo" usa el campo stockBajo que calcula el servidor; el panel no recalcula reglas.
import * as api from '../api.js';
import { h, tabla, medidor, dinero, fecha, entero, mensaje, vacio } from '../ui.js';

export async function render(raiz, yo) {
  const admin = yo.rol === 'ADMIN';
  const [todos, ventasResultado] = await Promise.all([
    api.get('/api/productos'),
    admin ? api.get('/api/ventas').catch((e) => e) : Promise.resolve(null),
  ]);

  // Los productos desactivados no cuentan en el panel ni generan alertas
  const productos = todos.filter((p) => p.activo !== false);
  const bajos = productos.filter((p) => p.stockBajo);
  const agotados = productos.filter((p) => (p.stock ?? 0) <= 0);
  const vencidos = productos.filter((p) => p.vencido === true || p.vencidoPorFecha === true);

  raiz.append(
    h('div', { class: 'franja' },
      h('div', {}, h('strong', {}, entero(productos.length)), h('span', {}, 'Productos registrados')),
      h('div', { class: bajos.length ? 'alerta' : '' }, h('strong', {}, entero(bajos.length)), h('span', {}, 'Con stock bajo')),
      h('div', { class: agotados.length ? 'critico' : '' }, h('strong', {}, entero(agotados.length)), h('span', {}, 'Agotados')),
      h('div', { class: vencidos.length ? 'critico' : '' }, h('strong', {}, entero(vencidos.length)), h('span', {}, 'Vencidos'))),
    h('div', { class: 'dos-columnas' }, seccionReponer(bajos), seccionVentas(ventasResultado, admin)));
}

function seccionReponer(bajos) {
  const orden = [...bajos].sort((a, b) => (a.stock ?? 0) - (b.stock ?? 0)).slice(0, 8);
  return h('section', { class: 'seccion' },
    h('div', { class: 'seccion__cabecera' }, h('h2', {}, 'Para reponer'),
      h('a', { href: '#/productos' }, 'Ver productos')),
    h('div', { class: orden.length ? '' : 'seccion__cuerpo' },
      orden.length
        ? tabla([
          { titulo: 'Producto', principal: true, celda: (p) => p.nombre },
          { titulo: 'Stock', celda: (p) => medidor(p) },
        ], orden)
        : vacio('Todo en orden', 'Ningún producto está en su stock mínimo o por debajo.')),
    bajos.length > orden.length
      ? h('p', { class: 'seccion__cuerpo suave pequeno' }, `Se muestran ${orden.length} de ${bajos.length} productos con stock bajo.`)
      : null);
}

function seccionVentas(resultado, admin) {
  const cabecera = h('div', { class: 'seccion__cabecera' }, h('h2', {}, 'Últimas ventas'),
    h('a', { href: '#/ventas' }, admin ? 'Ver historial' : 'Registrar venta'));
  if (!admin) {
    return h('section', { class: 'seccion' }, cabecera,
      h('div', { class: 'seccion__cuerpo' },
        h('p', { class: 'suave' }, 'El historial de ventas lo consulta un administrador. Desde Ventas puedes registrar una venta nueva.')));
  }
  if (resultado instanceof Error) {
    return h('section', { class: 'seccion' }, cabecera, h('div', { class: 'seccion__cuerpo' }, mensaje('error', resultado.message)));
  }
  const recientes = [...resultado].sort((a, b) => new Date(b.fechaVenta) - new Date(a.fechaVenta)).slice(0, 5);
  return h('section', { class: 'seccion' }, cabecera,
    recientes.length
      ? tabla([
        { titulo: 'Venta', principal: true, celda: (v) => `#${v.id}` },
        { titulo: 'Fecha', celda: (v) => fecha(v.fechaVenta) },
        { titulo: 'Total', num: true, celda: (v) => dinero(v.total) },
        { titulo: 'Estado', celda: (v) => h('span', { class: `insignia ${v.estado === 'CANCELADA' ? 'insignia--error' : 'insignia--ok'}` }, v.estado === 'CANCELADA' ? 'Cancelada' : 'Activa') },
      ], recientes)
      : h('div', { class: 'seccion__cuerpo' }, vacio('Aún no hay ventas', 'Cuando registres la primera aparecerá aquí.')));
}
