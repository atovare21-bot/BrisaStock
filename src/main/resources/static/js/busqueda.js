// Búsqueda rápida (Ctrl + K o la tecla /): un solo cuadro para ir a una sección o
// encontrar un producto desde cualquier pantalla.
//   GET /api/productos   (ADMIN y USER)  -> se filtra en el navegador, sin tildes ni mayúsculas
// Solo ofrece las secciones que el rol puede ver; la autorización real sigue en el backend.
import * as api from './api.js';
import { h, vaciar, icono, dialogo, dinero, entero, insigniaEstado, normalizar, mensaje } from './ui.js';

const MAX_PRODUCTOS = 8;

/**
 * @param rutas     secciones permitidas para el rol: [{id, titulo, icono}]
 * @param acciones  accesos directos extra: [{titulo, detalle, claves, icono, ejecutar}]
 */
export function abrirBusqueda({ rutas, acciones = [] }) {
  let productos = null;          // null = todavía cargando
  let errorCarga = null;
  let opciones = [];             // opciones visibles, en orden
  let activa = 0;

  const entrada = h('input', {
    type: 'search', id: 'busqueda-rapida', class: 'busqueda__entrada', autocomplete: 'off', spellcheck: 'false',
    placeholder: 'Escribe un producto, una marca o una sección', maxlength: 100,
    role: 'combobox', 'aria-expanded': 'true', 'aria-controls': 'busqueda-lista', 'aria-autocomplete': 'list',
    'aria-label': 'Búsqueda rápida',
  });
  const lista = h('div', { id: 'busqueda-lista', class: 'busqueda__lista', role: 'listbox', 'aria-label': 'Resultados' });
  const resumen = h('p', { class: 'suave pequeno busqueda__resumen', role: 'status' });

  const d = dialogo({
    titulo: 'Búsqueda rápida',
    cuerpo: [entrada, lista, resumen],
    acciones: [h('span', { class: 'suave pequeno busqueda__ayuda' },
      h('kbd', {}, '↑'), h('kbd', {}, '↓'), ' para moverte · ', h('kbd', {}, 'Enter'), ' para abrir · ', h('kbd', {}, 'Esc'), ' para cerrar')],
  });
  d.el.classList.add('busqueda');

  function elegir(opcion) {
    d.cerrar();
    opcion.ejecutar();
  }

  function opcionesPara(texto) {
    const q = normalizar(texto.trim());
    const coincide = (...campos) => !q || campos.some((c) => normalizar(c).includes(q));
    const grupos = [];

    const secciones = rutas.filter((r) => coincide(r.titulo))
      .map((r) => ({ titulo: `Ir a ${r.titulo}`, icono: r.icono, ejecutar: () => { location.hash = `#/${r.id}`; } }));
    if (secciones.length) grupos.push(['Secciones', secciones]);

    const accesos = acciones.filter((a) => coincide(a.titulo, a.detalle, a.claves));   // claves: sinónimos que no se muestran
    if (accesos.length) grupos.push(['Acciones', accesos]);

    if (q && productos) {
      const porId = /^\d+$/.test(q) ? Number(q) : null;
      const hallados = productos
        .filter((p) => p.id === porId || coincide(p.nombre, p.marca, p.categoria))
        .sort((a, b) => (b.id === porId) - (a.id === porId) || (a.activo === false) - (b.activo === false));
      const visibles = hallados.slice(0, MAX_PRODUCTOS).map((p) => ({
        titulo: p.nombre,
        detalle: [p.marca, p.categoria, `Stock ${entero(p.stock ?? 0)}`, dinero(p.precioVenta)].filter(Boolean).join(' · '),
        icono: 'productos', estado: insigniaEstado(p),
        ejecutar: async () => { (await import('./views/productos.js')).verDetalle(p.id, api.esAdmin()); },
      }));
      if (visibles.length) grupos.push([`Productos${hallados.length > visibles.length ? ` (${visibles.length} de ${hallados.length})` : ''}`, visibles]);
    }
    return grupos;
  }

  function pintar() {
    const texto = entrada.value;
    const grupos = opcionesPara(texto);
    opciones = grupos.flatMap(([, items]) => items);
    activa = Math.min(activa, Math.max(0, opciones.length - 1));
    vaciar(lista);

    let n = 0;
    grupos.forEach(([titulo, items]) => {
      lista.append(h('div', { class: 'busqueda__grupo', role: 'presentation' }, titulo));
      items.forEach((op) => {
        const i = n++;
        lista.append(h('div', {
          id: `busqueda-op-${i}`, class: 'busqueda__opcion', role: 'option', 'aria-selected': String(i === activa),
          onclick: () => elegir(op), onmousemove: () => { if (activa !== i) marcar(i); },
        },
        icono(op.icono),
        h('span', { class: 'busqueda__texto' }, op.titulo, op.detalle ? h('span', { class: 'sub' }, op.detalle) : null),
        op.estado || null));
      });
    });

    if (errorCarga) lista.append(mensaje('error', `No se pudieron cargar los productos. ${errorCarga}`));
    if (!opciones.length && !errorCarga) {
      lista.append(h('div', { class: 'vacio' }, h('strong', {}, 'Sin coincidencias'),
        productos ? 'Prueba con otra palabra o con el número (ID) del producto.' : 'Cargando productos…'));
    }
    const hayTexto = texto.trim() !== '';
    resumen.textContent = !hayTexto
      ? 'Escribe para buscar entre los productos.'
      : (productos ? `${opciones.length} resultado${opciones.length === 1 ? '' : 's'}.` : 'Cargando productos…');
    entrada.setAttribute('aria-activedescendant', opciones.length ? `busqueda-op-${activa}` : '');
  }

  function marcar(i) {
    activa = i;
    lista.querySelectorAll('.busqueda__opcion').forEach((el, n) => el.setAttribute('aria-selected', String(n === activa)));
    entrada.setAttribute('aria-activedescendant', `busqueda-op-${activa}`);
    lista.querySelector(`#busqueda-op-${activa}`)?.scrollIntoView({ block: 'nearest' });
  }

  entrada.addEventListener('input', () => { activa = 0; pintar(); });
  entrada.addEventListener('keydown', (ev) => {
    if (ev.key === 'ArrowDown' || ev.key === 'ArrowUp') {
      ev.preventDefault();
      if (!opciones.length) return;
      const paso = ev.key === 'ArrowDown' ? 1 : -1;
      marcar((activa + paso + opciones.length) % opciones.length);
    } else if (ev.key === 'Enter') {
      ev.preventDefault();
      if (opciones[activa]) elegir(opciones[activa]);
    }
  });

  pintar();
  entrada.focus();
  api.get('/api/productos')
    .then((datos) => { productos = datos; })
    .catch((e) => { productos = []; errorCarga = e.message; })
    .finally(() => { if (d.el.open) pintar(); });
}
