// Arranque: inicio de sesión, menú según el rol y enrutado por hash (#/productos).
// Ocultar opciones por rol es solo comodidad: la autorización real la hace el backend.
import * as api from './api.js';
import { h, icono, vaciar, panelError, aviso } from './ui.js';
import { abrirPanelAccesibilidad } from './accesibilidad.js';   // al importarse aplica las preferencias guardadas
import { abrirBusqueda } from './busqueda.js';

const RUTAS = [
  { id: 'panel', titulo: 'Panel', icono: 'panel', roles: ['ADMIN', 'USER'], cargar: () => import('./views/panel.js') },
  { id: 'productos', titulo: 'Productos', icono: 'productos', roles: ['ADMIN', 'USER'], cargar: () => import('./views/productos.js') },
  { id: 'ventas', titulo: 'Ventas', icono: 'ventas', roles: ['ADMIN', 'USER'], cargar: () => import('./views/ventas.js') },
  { id: 'inventario', titulo: 'Inventario', icono: 'inventario', roles: ['ADMIN', 'USER'], cargar: () => import('./views/inventario.js') },
  { id: 'reportes', titulo: 'Reportes', icono: 'reportes', roles: ['ADMIN'], cargar: () => import('./views/reportes.js') },
  { id: 'usuarios', titulo: 'Usuarios', icono: 'usuarios', roles: ['ADMIN'], cargar: () => import('./views/usuarios.js') },
];
const ROL_TEXTO = { ADMIN: 'Administrador', USER: 'Usuario' };

const $ = (id) => document.getElementById(id);
let rutaActual = null;
let numeroRender = 0;

function mostrarLogin(mensajeError) {
  $('app').hidden = true;
  $('login').hidden = false;
  document.body.classList.remove('menu-abierto');
  const err = $('login-error');
  err.hidden = !mensajeError;
  err.textContent = mensajeError || '';
  $('login-clave').value = '';
  ($('login-usuario').value ? $('login-clave') : $('login-usuario')).focus();
}

function rutasPermitidas() {
  const rol = api.sesionActual()?.rol;
  return RUTAS.filter((r) => r.roles.includes(rol));
}

function construirMenu() {
  const menu = vaciar($('menu'));
  rutasPermitidas().forEach((r) => {
    menu.append(h('a', { href: `#/${r.id}`, 'data-ruta': r.id }, icono(r.icono), r.titulo));
  });
}

async function mostrarVista() {
  const permitidas = rutasPermitidas();
  const id = location.hash.replace(/^#\//, '') || 'panel';
  const ruta = permitidas.find((r) => r.id === id) || permitidas[0];
  if (ruta.id !== id) { location.replace(`#/${ruta.id}`); return; }

  rutaActual = ruta.id;
  const mio = ++numeroRender;
  document.querySelectorAll('#menu a').forEach((a) => {
    if (a.dataset.ruta === ruta.id) a.setAttribute('aria-current', 'page'); else a.removeAttribute('aria-current');
  });
  $('titulo-vista').textContent = ruta.titulo;
  document.title = `${ruta.titulo} · BrisaStock`;
  cerrarMenuMovil();

  const raiz = vaciar($('vista'));
  try {
    const modulo = await ruta.cargar();
    if (mio !== numeroRender) return;            // el usuario ya navegó a otra pantalla
    await modulo.render(raiz, api.sesionActual());
  } catch (e) {
    if (mio !== numeroRender) return;
    vaciar(raiz).append(panelError(e, mostrarVista));
  }
  raiz.focus({ preventScroll: true });
}

function abrirMenuMovil() {
  document.body.classList.add('menu-abierto');
  $('velo').hidden = false;
  $('btn-menu').setAttribute('aria-expanded', 'true');
}
function cerrarMenuMovil() {
  document.body.classList.remove('menu-abierto');
  $('velo').hidden = true;
  $('btn-menu').setAttribute('aria-expanded', 'false');
}

// ---------- Búsqueda rápida y accesibilidad ----------
function busquedaRapida() {
  if (!api.sesionActual() || document.querySelector('dialog[open]')) return;
  const rutas = rutasPermitidas();
  const tiene = (id) => rutas.some((r) => r.id === id);
  abrirBusqueda({
    rutas,
    acciones: [
      tiene('ventas') ? { titulo: 'Registrar una venta', detalle: 'Elegir productos y cobrar', claves: 'vender venta cobrar caja', icono: 'ventas', ejecutar: () => { location.hash = '#/ventas'; } } : null,
      tiene('inventario') ? { titulo: 'Registrar entrada de mercancía', detalle: 'Sumar al inventario lo que llegó', claves: 'llegada pedido proveedor inventario stock', icono: 'inventario', ejecutar: () => { location.hash = '#/inventario'; } } : null,
      { titulo: 'Opciones de accesibilidad', detalle: 'Tamaño de letra y alto contraste', claves: 'letra grande zoom contraste accesibilidad', icono: 'accesibilidad', ejecutar: abrirPanelAccesibilidad },
    ].filter(Boolean),
  });
}

function atajosDeTeclado(ev) {
  const escribiendo = ev.target instanceof Element && ev.target.closest('input, textarea, select, [contenteditable]');
  const ctrlK = (ev.ctrlKey || ev.metaKey) && !ev.altKey && ev.key.toLowerCase() === 'k';
  const barra = ev.key === '/' && !ev.ctrlKey && !ev.metaKey && !ev.altKey && !escribiendo;
  if (!ctrlK && !barra) return;
  if (!api.sesionActual()) return;
  ev.preventDefault();          // Ctrl+K no debe abrir la barra de búsqueda del navegador
  busquedaRapida();
}

function entrarALaApp(usuario) {
  $('login').hidden = true;
  $('app').hidden = false;
  $('usuario-nombre').textContent = usuario.nombre || usuario.username;
  $('usuario-rol').textContent = ROL_TEXTO[usuario.rol] || usuario.rol;
  construirMenu();
  if (!location.hash) location.hash = '#/panel';
  mostrarVista();
}

function salir(mensaje) {
  api.cerrarSesion();
  document.querySelectorAll('dialog[open]').forEach((d) => d.close());   // nada de la sesión anterior queda a la vista
  numeroRender++;
  location.hash = '';
  vaciar($('vista'));
  mostrarLogin(mensaje);
}

function iniciar() {
  $('btn-menu').append(icono('menu'));
  $('btn-menu').addEventListener('click', () => (document.body.classList.contains('menu-abierto') ? cerrarMenuMovil() : abrirMenuMovil()));
  $('velo').addEventListener('click', cerrarMenuMovil);
  $('btn-buscar').prepend(icono('buscar'));
  $('btn-buscar').addEventListener('click', busquedaRapida);
  $('btn-accesibilidad').prepend(icono('accesibilidad'));
  $('btn-accesibilidad').addEventListener('click', abrirPanelAccesibilidad);
  $('btn-accesibilidad-login').prepend(icono('accesibilidad'));
  $('btn-accesibilidad-login').addEventListener('click', abrirPanelAccesibilidad);
  document.addEventListener('keydown', atajosDeTeclado);
  $('btn-salir').addEventListener('click', () => { salir(); aviso('Cerraste sesión.', 'info', 3000); });
  window.addEventListener('hashchange', () => { if (api.sesionActual()) mostrarVista(); });
  api.alSesionInvalida(() => salir('Tu sesión ya no es válida. Inicia sesión de nuevo.'));

  $('form-login').addEventListener('submit', async (ev) => {
    ev.preventDefault();
    const usuario = $('login-usuario').value.trim();
    const clave = $('login-clave').value;
    const err = $('login-error');
    if (!usuario || !clave) {
      err.textContent = 'Escribe tu usuario y tu contraseña.';
      err.hidden = false;
      return;
    }
    const boton = ev.submitter;
    boton.disabled = true;
    err.hidden = true;
    try {
      const yo = await api.iniciarSesion(usuario, clave);
      $('login-clave').value = '';
      entrarALaApp(yo);
    } catch (e) {
      err.textContent = e.status === 401
        ? 'Usuario o contraseña incorrectos, o el usuario está desactivado.'
        : e.message;
      err.hidden = false;
      $('login-clave').value = '';
      $('login-clave').focus();
    } finally {
      boton.disabled = false;
    }
  });

  mostrarLogin();
}

iniciar();
export { rutaActual };
