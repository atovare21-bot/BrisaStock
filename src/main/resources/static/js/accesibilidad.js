// Opciones de accesibilidad: tamaño de letra, alto contraste, controles grandes y
// menos movimiento. Son preferencias de presentación de ESTE dispositivo: se guardan en
// localStorage (nunca credenciales ni datos del negocio) y no viajan al servidor.
// Si el navegador bloquea localStorage, las opciones funcionan igual pero no se recuerdan.
import { h, dialogo } from './ui.js';

const CLAVE = 'brisastock.accesibilidad';

export const TAMANOS = [
  { id: 'normal', texto: 'Normal', escala: 1 },
  { id: 'grande', texto: 'Grande', escala: 1.15 },
  { id: 'muy-grande', texto: 'Muy grande', escala: 1.3 },
  { id: 'maximo', texto: 'Máximo', escala: 1.5 },
];
const POR_DEFECTO = { tamano: 'normal', contraste: false, controlesGrandes: false, menosMovimiento: false };

let prefs = leer();

function leer() {
  try {
    const guardado = JSON.parse(localStorage.getItem(CLAVE) || '{}');
    const limpio = { ...POR_DEFECTO };
    if (TAMANOS.some((t) => t.id === guardado.tamano)) limpio.tamano = guardado.tamano;
    ['contraste', 'controlesGrandes', 'menosMovimiento'].forEach((k) => { limpio[k] = guardado[k] === true; });
    return limpio;
  } catch {
    return { ...POR_DEFECTO };
  }
}

function guardar() {
  try { localStorage.setItem(CLAVE, JSON.stringify(prefs)); } catch { /* sin almacenamiento: solo esta visita */ }
}

/** Aplica las preferencias al documento mediante atributos que usa la hoja de estilos. */
export function aplicar() {
  const raiz = document.documentElement;
  const tam = TAMANOS.find((t) => t.id === prefs.tamano) || TAMANOS[0];
  raiz.style.setProperty('--escala', String(tam.escala));
  raiz.dataset.letra = tam.escala >= 1.3 ? 'amplia' : 'normal';
  raiz.dataset.contraste = prefs.contraste ? 'alto' : 'normal';
  raiz.dataset.controles = prefs.controlesGrandes ? 'grandes' : 'normales';
  raiz.dataset.movimiento = prefs.menosMovimiento ? 'reducido' : 'normal';
}

function cambiar(cambios) {
  prefs = { ...prefs, ...cambios };
  guardar();
  aplicar();
}

export const preferencias = () => ({ ...prefs });

/** Panel de accesibilidad. Los cambios se aplican al instante, sin botón de guardar. */
export function abrirPanelAccesibilidad() {
  const estado = h('p', { class: 'suave pequeno', role: 'status', style: 'margin:1rem 0 0' },
    'Los cambios se aplican al instante y se recuerdan en este dispositivo.');

  const botonesTamano = TAMANOS.map((t) => h('button', {
    type: 'button', class: 'opcion-letra', role: 'radio', 'data-tamano': t.id,
    'aria-checked': String(prefs.tamano === t.id), tabindex: prefs.tamano === t.id ? '0' : '-1',
    onclick: () => elegirTamano(t.id),
  }, h('span', { class: 'opcion-letra__muestra', style: `font-size:${t.escala}em`, 'aria-hidden': 'true' }, 'Aa'), t.texto));

  function elegirTamano(id, enfocar = false) {
    cambiar({ tamano: id });
    botonesTamano.forEach((b) => {
      const activo = b.dataset.tamano === id;
      b.setAttribute('aria-checked', String(activo));
      b.tabIndex = activo ? 0 : -1;
      if (activo && enfocar) b.focus();
    });
  }

  const grupoTamano = h('div', { class: 'opciones-letra', role: 'radiogroup', 'aria-labelledby': 'acc-letra' }, botonesTamano);
  // Como un grupo de radios: las flechas cambian la opción
  grupoTamano.addEventListener('keydown', (ev) => {
    const paso = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 }[ev.key];
    if (!paso) return;
    ev.preventDefault();
    const i = TAMANOS.findIndex((t) => t.id === prefs.tamano);
    elegirTamano(TAMANOS[(i + paso + TAMANOS.length) % TAMANOS.length].id, true);
  });

  const interruptor = (clave, titulo, ayuda) => {
    const id = `acc-${clave}`;
    const caja = h('input', { type: 'checkbox', id, checked: prefs[clave], 'aria-describedby': `${id}-ayuda` });
    caja.addEventListener('change', () => cambiar({ [clave]: caja.checked }));
    return {
      caja,
      nodo: h('div', { class: 'opcion-acc' }, caja,
        h('div', {}, h('label', { for: id }, titulo), h('span', { class: 'ayuda', id: `${id}-ayuda` }, ayuda))),
    };
  };
  const contraste = interruptor('contraste', 'Alto contraste', 'Texto más oscuro y bordes más marcados para leer mejor.');
  const controles = interruptor('controlesGrandes', 'Botones y campos más grandes', 'Más fácil de tocar con el dedo o con poca precisión.');
  const movimiento = interruptor('menosMovimiento', 'Reducir animaciones', 'Quita los movimientos de la pantalla.');

  const restablecer = h('button', {
    class: 'btn', type: 'button',
    onclick: () => {
      cambiar({ ...POR_DEFECTO });
      elegirTamano(POR_DEFECTO.tamano);
      contraste.caja.checked = controles.caja.checked = movimiento.caja.checked = false;
      estado.textContent = 'Se restablecieron las opciones iniciales.';
    },
  }, 'Restablecer');
  const cerrar = h('button', { class: 'btn btn--primario', type: 'button', onclick: () => d.cerrar() }, 'Listo');

  const atajo = (teclas, texto) => h('li', {}, teclas.map((t) => h('kbd', {}, t)), ' ', texto);

  const d = dialogo({
    titulo: 'Accesibilidad',
    cuerpo: [
      h('h3', { id: 'acc-letra', class: 'acc-titulo' }, 'Tamaño de la letra'),
      grupoTamano,
      h('h3', { class: 'acc-titulo' }, 'Lectura y uso'),
      contraste.nodo, controles.nodo, movimiento.nodo,
      h('h3', { class: 'acc-titulo' }, 'Atajos del teclado'),
      h('ul', { class: 'atajos' },
        atajo(['Ctrl', 'K'], 'o la tecla / abren la búsqueda rápida.'),
        atajo(['↑', '↓'], 'recorren los resultados y Enter abre el elegido.'),
        atajo(['Esc'], 'cierra cualquier ventana.'),
        atajo(['Tab'], 'pasa al siguiente botón o campo.')),
      estado,
    ],
    acciones: [restablecer, cerrar],
  });
  (botonesTamano.find((b) => b.tabIndex === 0) || botonesTamano[0]).focus();
}

aplicar();   // al cargar el módulo, antes de mostrar la primera pantalla
