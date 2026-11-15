// Utilidades de interfaz. Todo el contenido dinámico se inserta con textContent
// (nunca innerHTML con datos), así que los datos de la API no pueden inyectar HTML.

export function h(etiqueta, props = {}, ...hijos) {
  const el = document.createElement(etiqueta);
  for (const [clave, valor] of Object.entries(props || {})) {
    if (valor === null || valor === undefined || valor === false) continue;
    if (clave === 'class') el.className = valor;
    else if (clave.startsWith('on') && typeof valor === 'function') el.addEventListener(clave.slice(2).toLowerCase(), valor);
    else if (['value', 'checked', 'disabled', 'selected', 'hidden'].includes(clave)) el[clave] = valor;
    else if (valor === true) el.setAttribute(clave, '');
    else el.setAttribute(clave, valor);
  }
  agregar(el, hijos);
  return el;
}

function agregar(el, hijos) {
  for (const hijo of hijos.flat(Infinity)) {
    if (hijo === null || hijo === undefined || hijo === false) continue;
    el.append(hijo instanceof Node ? hijo : document.createTextNode(String(hijo)));
  }
}

export const vaciar = (el) => { el.replaceChildren(); return el; };

/** Texto en minúsculas y sin tildes, para que "cafe" encuentre "Café". */
export const normalizar = (texto) => String(texto ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();

// ---------- Formato ----------
const moneda = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', maximumFractionDigits: 0 });
const monedaDec = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', minimumFractionDigits: 0, maximumFractionDigits: 2 });
export const dinero = (n) => (n === null || n === undefined ? '—' : monedaDec.format(n));
export const dineroEntero = (n) => (n === null || n === undefined ? '—' : moneda.format(n));
export const entero = (n) => (n === null || n === undefined ? '—' : new Intl.NumberFormat('es-CO').format(n));
export function fecha(texto) {
  if (!texto) return '—';
  const d = new Date(texto);
  if (Number.isNaN(d.getTime())) return String(texto);
  return d.toLocaleString('es-CO', { dateStyle: 'medium', timeStyle: 'short' });
}

// ---------- Íconos (cadenas fijas del propio código, no datos externos) ----------
const ICONOS = {
  panel: '<path d="M3 13h8V3H3zM13 21h8V11h-8zM13 3v6h8V3zM3 21h8v-6H3z"/>',
  productos: '<path d="M21 8 12 3 3 8v8l9 5 9-5zM3 8l9 5 9-5M12 13v8"/>',
  ventas: '<path d="M6 6h15l-1.5 9H8zM6 6 5 3H2M9 20a1 1 0 1 0 0-.01M18 20a1 1 0 1 0 0-.01"/>',
  inventario: '<path d="M4 7h16M4 12h16M4 17h10M18 15v6M15 18h6"/>',
  reportes: '<path d="M5 21V10M12 21V4M19 21v-7"/>',
  usuarios: '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8M22 21v-2a4 4 0 0 0-3-3.9M16 3.1a4 4 0 0 1 0 7.8"/>',
  menu: '<path d="M4 6h16M4 12h16M4 18h16"/>',
  cerrar: '<path d="M6 6l12 12M18 6 6 18"/>',
  buscar: '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.6-3.6"/>',
  accesibilidad: '<circle cx="12" cy="4.5" r="1.6"/><path d="M4.5 8.5 12 10l7.5-1.5M12 10v5.5M12 15.5 8.5 21M12 15.5l3.5 5.5"/>',
};
export function icono(nombre) {
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('fill', 'none');
  svg.setAttribute('stroke', 'currentColor');
  svg.setAttribute('stroke-width', '2');
  svg.setAttribute('stroke-linecap', 'round');
  svg.setAttribute('stroke-linejoin', 'round');
  svg.setAttribute('aria-hidden', 'true');
  svg.innerHTML = ICONOS[nombre] || '';   // constante interna
  return svg;
}

// ---------- Avisos ----------
export function aviso(texto, tipo = 'info', ms = 5000) {
  const contenedor = document.getElementById('avisos');
  const el = h('div', { class: `aviso aviso--${tipo}`, role: tipo === 'error' ? 'alert' : 'status' }, texto);
  contenedor.append(el);
  setTimeout(() => el.remove(), tipo === 'error' ? Math.max(ms, 8000) : ms);
}

// ---------- Diálogos ----------
let contadorDialogos = 0;
export function dialogo({ titulo, cuerpo, acciones = [], ancho = false, alCerrar }) {
  // Id único: puede haber un diálogo abierto encima de otro (p. ej. una confirmación)
  const idTitulo = `dlg-titulo-${++contadorDialogos}`;
  const dlg = h('dialog', { class: ancho ? 'ancho' : '', 'aria-labelledby': idTitulo });
  const cerrar = () => { if (dlg.open) dlg.close(); };
  dlg.append(
    h('div', { class: 'dialogo__cabecera' },
      h('h2', { id: idTitulo }, titulo),
      h('button', { class: 'btn btn--icono btn--suave', type: 'button', 'aria-label': 'Cerrar', onclick: cerrar }, icono('cerrar'))),
    h('div', { class: 'dialogo__cuerpo' }, cuerpo),
    acciones.length ? h('div', { class: 'dialogo__pie' }, acciones) : null,
  );
  dlg.addEventListener('close', () => { dlg.remove(); alCerrar?.(); });
  document.body.append(dlg);
  dlg.showModal();
  return { el: dlg, cerrar };
}

/** Confirmación antes de operaciones delicadas. Devuelve {ok, texto}. */
export function confirmar({ titulo, mensaje, textoConfirmar = 'Confirmar', peligro = false, pedirTexto = null }) {
  return new Promise((resolver) => {
    let resultado = { ok: false };
    const area = pedirTexto
      ? h('div', { class: 'campo' },
        h('label', { for: 'conf-texto' }, pedirTexto.etiqueta),
        h('textarea', { id: 'conf-texto', maxlength: pedirTexto.max || 500, placeholder: pedirTexto.ayuda || '' }),
        pedirTexto.requerido ? h('span', { class: 'error', id: 'conf-error', hidden: true }, 'Escribe el motivo para continuar.') : null)
      : null;
    const cancelar = h('button', { class: 'btn', type: 'button', onclick: () => d.cerrar() }, 'Cancelar');
    const aceptar = h('button', {
      class: `btn ${peligro ? 'btn--peligro' : 'btn--primario'}`, type: 'button',
      onclick: () => {
        const texto = area ? area.querySelector('textarea').value.trim() : undefined;
        if (pedirTexto?.requerido && !texto) {
          area.querySelector('#conf-error').hidden = false;
          area.querySelector('textarea').focus();
          return;
        }
        resultado = { ok: true, texto };
        d.cerrar();
      },
    }, textoConfirmar);
    const d = dialogo({
      titulo, cuerpo: [h('p', {}, mensaje), area], acciones: [cancelar, aceptar],
      alCerrar: () => resolver(resultado),
    });
    cancelar.focus();
  });
}

// ---------- Formularios ----------
let contadorCampos = 0;
/** Campo con etiqueta, ayuda y espacio para el error. data-campo une el error del servidor al campo. */
export function campo(etiqueta, control, { nombre, ayuda } = {}) {
  const id = `c${++contadorCampos}`;
  control.id = id;
  if (nombre) control.name = nombre;
  return h('div', { class: 'campo', 'data-campo': nombre || '' },
    h('label', { for: id }, etiqueta),
    control,
    ayuda ? h('span', { class: 'ayuda' }, ayuda) : null,
    h('span', { class: 'error', hidden: true }));
}

export function mostrarErrorCampo(contenedor, nombre, mensaje) {
  const c = contenedor.querySelector(`.campo[data-campo="${nombre}"]`);
  if (!c) return false;
  c.classList.add('invalido');
  const e = c.querySelector('.error');
  e.textContent = mensaje;
  e.hidden = false;
  // Los lectores de pantalla anuncian el campo como inválido y leen el motivo
  const control = c.querySelector('input, select, textarea');
  if (control) {
    if (!e.id) e.id = `${control.id || 'campo'}-error`;
    control.setAttribute('aria-invalid', 'true');
    control.setAttribute('aria-describedby', e.id);
  }
  return true;
}

export function limpiarErrores(contenedor) {
  contenedor.querySelectorAll('[aria-invalid="true"]').forEach((el) => {
    el.removeAttribute('aria-invalid');
    el.removeAttribute('aria-describedby');
  });
  contenedor.querySelectorAll('.campo.invalido').forEach((c) => c.classList.remove('invalido'));
  contenedor.querySelectorAll('.campo .error').forEach((e) => { e.hidden = true; e.textContent = ''; });
}

/** Ejecuta fn mientras el botón queda desactivado con indicador. */
export async function ocupado(boton, fn) {
  boton.disabled = true;
  boton.classList.add('ocupado');
  try { return await fn(); } finally { boton.disabled = false; boton.classList.remove('ocupado'); }
}

// ---------- Piezas compartidas ----------
export function mensaje(tipo, texto) {
  return h('p', { class: `mensaje mensaje--${tipo}`, role: tipo === 'error' ? 'alert' : 'status' }, texto);
}

export function vacio(titulo, detalle) {
  return h('div', { class: 'vacio' }, h('strong', {}, titulo), detalle || '');
}

/** Medidor: stock frente al stock mínimo propio del producto (todo viene de la API). */
export function medidor(p) {
  const stock = p.stock ?? 0;
  const minimo = p.stockMinimo ?? 0;
  const tope = Math.max(stock, minimo * 2, 1);
  const clase = stock === 0 ? 'agotado' : (p.stockBajo ? 'bajo' : '');
  const barra = h('span', {
    class: `medidor ${clase}`, role: 'img',
    'aria-label': `Stock ${stock}, mínimo ${minimo}${p.stockBajo ? ', stock bajo' : ''}`,
  }, h('i', { style: `width:${Math.min(100, (stock / tope) * 100)}%` }),
  minimo > 0 ? h('b', { style: `left:${Math.min(100, (minimo / tope) * 100)}%` }) : null);
  return h('div', { class: 'medidor-celda' },
    h('div', { class: 'cifras' }, h('span', {}, entero(stock)), h('small', {}, `mín. ${entero(p.stockMinimo ?? 0)}`)),
    barra);
}

export function insigniaEstado(p) {
  if (p.activo === false) return h('span', { class: 'insignia' }, 'Desactivado');
  if (p.vencido || p.estado === 'Vencido' || p.vencidoPorFecha) return h('span', { class: 'insignia insignia--error' }, 'Vencido');
  if (p.estado === 'Agotado' || (p.stock ?? 0) <= 0) return h('span', { class: 'insignia insignia--error' }, 'Agotado');
  if (p.stockBajo) return h('span', { class: 'insignia insignia--aviso' }, 'Stock bajo');
  return h('span', { class: 'insignia insignia--ok' }, p.estado || 'Disponible');
}

const TIPOS_MOV = {
  ENTRADA: ['Entrada', 'ok'], SALIDA: ['Salida', 'aviso'], VENTA: ['Venta', 'info'],
  AJUSTE: ['Ajuste', 'neutro'], DEVOLUCION: ['Devolución', 'info'],
};
export function insigniaTipo(tipo) {
  const [texto, clase] = TIPOS_MOV[tipo] || [tipo, 'neutro'];
  return h('span', { class: `insignia insignia--${clase}` }, texto);
}

/** Tabla genérica. columnas: [{titulo, num, principal, celda:(fila)=>nodo|texto}] */
export function tabla(columnas, filas, { vacioTitulo = 'Sin resultados', vacioDetalle = '' } = {}) {
  if (!filas.length) return vacio(vacioTitulo, vacioDetalle);
  return h('div', { class: 'tabla-caja' },
    h('table', { class: 'apilable' },
      h('thead', {}, h('tr', {}, columnas.map((c) => h('th', { class: c.num ? 'num' : '', scope: 'col' }, c.titulo)))),
      h('tbody', {}, filas.map((fila) => h('tr', {}, columnas.map((c) => h('td', {
        class: [c.num ? 'num' : '', c.principal ? 'principal' : '', c.acciones ? 'acciones' : ''].join(' ').trim(),
        'data-etiqueta': c.titulo,
      }, c.celda(fila))))))));
}

/** Muestra un error de API dentro de un contenedor con opción de reintentar. */
export function panelError(err, reintentar) {
  return h('div', {},
    mensaje('error', err.message || 'Ocurrió un error inesperado.'),
    reintentar ? h('button', { class: 'btn', type: 'button', onclick: reintentar }, 'Reintentar') : null);
}

export function debounce(fn, ms = 350) {
  let t;
  return (...args) => { clearTimeout(t); t = setTimeout(() => fn(...args), ms); };
}
