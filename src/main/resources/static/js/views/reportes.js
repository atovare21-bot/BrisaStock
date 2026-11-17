// Reportes disponibles en el backend (solo ADMIN).
import * as api from '../api.js';
import { h, vaciar, tabla, medidor, dinero, entero, insigniaEstado, panelError } from '../ui.js';

const REPORTES = [
  { id: 'stock-bajo', titulo: 'Stock bajo', ayuda: 'Productos cuyo stock es igual o menor que su stock mínimo.' },
  { id: 'agotados', titulo: 'Agotados', ayuda: 'Productos sin unidades disponibles.' },
  { id: 'vencidos', titulo: 'Vencidos', ayuda: 'Productos marcados como vencidos.' },
  { id: 'ventas-por-dia', titulo: 'Ventas por día y vendedor', ayuda: 'Resumen de ventas activas por fecha y usuario.' },
];

export async function render(raiz) {
  const zona = h('div', { 'aria-live': 'polite' });
  const rangoVentas = { desde: '', hasta: '' };
  const botones = REPORTES.map((r) => h('button', { type: 'button', role: 'tab', 'aria-selected': 'false', onclick: () => cargar(r) }, r.titulo));
  raiz.append(h('div', { class: 'pestanas', role: 'tablist' }, botones), zona);

  async function cargar(r) {
    botones.forEach((b, i) => b.setAttribute('aria-selected', String(REPORTES[i] === r)));
    vaciar(zona);
    if (r.id === 'ventas-por-dia') {
      const desde = h('input', { type: 'date', value: rangoVentas.desde, 'aria-label': 'Fecha inicial' });
      const hasta = h('input', { type: 'date', value: rangoVentas.hasta, 'aria-label': 'Fecha final' });
      const formulario = h('form', { class: 'herramientas', onsubmit: (ev) => {
        ev.preventDefault();
        rangoVentas.desde = desde.value;
        rangoVentas.hasta = hasta.value;
        cargar(r);
      } },
      h('label', { class: 'campo' }, h('span', {}, 'Desde'), desde),
      h('label', { class: 'campo' }, h('span', {}, 'Hasta'), hasta),
      h('button', { class: 'btn btn--primario', type: 'submit' }, 'Consultar'));
      vaciar(zona).append(formulario);
    }
    zona.append(h('p', { class: 'suave' }, 'Cargando reporte…'));
    try {
      const parametros = new URLSearchParams(Object.entries(rangoVentas).filter(([, v]) => v));
      const consulta = r.id === 'ventas-por-dia' && parametros.size
        ? `ventas-por-dia?${parametros}`
        : r.id;
      const filas = await api.get(`/api/reportes/${consulta}`);
      if (r.id === 'ventas-por-dia') {
        vaciar(zona).append(h('section', { class: 'seccion' },
          h('div', { class: 'seccion__cabecera' }, h('div', {},
            h('h2', {}, `${r.titulo}: ${entero(filas.length)} grupo(s)`),
            h('p', { class: 'suave pequeno', style: 'margin:0' }, r.ayuda))),
          tabla([
            { titulo: 'Fecha', celda: (v) => v.fecha },
            { titulo: 'Vendedor', principal: true, celda: (v) => v.vendedor },
            { titulo: 'Ventas', num: true, celda: (v) => entero(v.cantidadVentas) },
            { titulo: 'Total', num: true, celda: (v) => dinero(v.total) },
          ], filas, { vacioTitulo: 'Sin ventas', vacioDetalle: 'No hay ventas activas en este rango.' })));
        return;
      }
      vaciar(zona).append(h('section', { class: 'seccion' },
        h('div', { class: 'seccion__cabecera' }, h('div', {}, h('h2', {}, `${r.titulo}: ${entero(filas.length)} producto(s)`), h('p', { class: 'suave pequeno', style: 'margin:0' }, r.ayuda))),
        tabla([
          { titulo: 'Producto', principal: true, celda: (p) => [p.nombre, h('span', { class: 'sub' }, [p.marca, p.categoria].filter(Boolean).join(' · '))] },
          { titulo: 'Stock', celda: (p) => medidor(p) },
          { titulo: 'Precio de venta', num: true, celda: (p) => dinero(p.precioVenta) },
          r.id === 'vencidos' ? { titulo: 'Vence', celda: (p) => p.fechaVencimiento || '—' } : null,
          { titulo: 'Estado', celda: (p) => insigniaEstado(p) },
        ].filter(Boolean), filas, { vacioTitulo: 'Sin resultados', vacioDetalle: 'Ningún producto cumple esta condición ahora mismo.' })));
    } catch (e) {
      vaciar(zona).append(panelError(e, () => cargar(r)));
    }
  }
  await cargar(REPORTES[0]);
}
