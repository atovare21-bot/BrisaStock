// Reportes existentes en el backend (solo ADMIN):
//   GET /api/reportes/stock-bajo, /api/reportes/agotados, /api/reportes/vencidos
import * as api from '../api.js';
import { h, vaciar, tabla, medidor, dinero, entero, insigniaEstado, panelError } from '../ui.js';

const REPORTES = [
  { id: 'stock-bajo', titulo: 'Stock bajo', ayuda: 'Productos cuyo stock es igual o menor que su stock mínimo.' },
  { id: 'agotados', titulo: 'Agotados', ayuda: 'Productos sin unidades disponibles.' },
  { id: 'vencidos', titulo: 'Vencidos', ayuda: 'Productos marcados como vencidos.' },
];

export async function render(raiz) {
  const zona = h('div', { 'aria-live': 'polite' });
  const botones = REPORTES.map((r) => h('button', { type: 'button', role: 'tab', 'aria-selected': 'false', onclick: () => cargar(r) }, r.titulo));
  raiz.append(h('div', { class: 'pestanas', role: 'tablist' }, botones), zona);

  async function cargar(r) {
    botones.forEach((b, i) => b.setAttribute('aria-selected', String(REPORTES[i] === r)));
    vaciar(zona).append(h('p', { class: 'suave' }, 'Cargando reporte…'));
    try {
      const filas = await api.get(`/api/reportes/${r.id}`);
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
