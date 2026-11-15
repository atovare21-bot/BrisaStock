// Cliente de la API REST de BrisaStock.
//
// Autenticación: HTTP Basic, igual que el backend. Las credenciales se guardan
// SOLO en memoria de esta pestaña (nunca en localStorage/sessionStorage ni en
// cookies): al recargar la página hay que volver a iniciar sesión.
// Se usa credentials: 'omit' para que el navegador no abra su propio diálogo
// de contraseña cuando el servidor responde 401 con WWW-Authenticate.

let sesion = null;          // { encabezado, usuario: {username, nombre, rol} }
let alExpirar = () => {};
let pendientes = 0;

export class ApiError extends Error {
  constructor(status, mensaje, errores = []) {
    super(mensaje);
    this.status = status;
    this.errores = errores;   // [{campo, mensaje}] en errores de validación (400)
  }
}

export const sesionActual = () => (sesion ? sesion.usuario : null);
export const esAdmin = () => sesion?.usuario.rol === 'ADMIN';
export function alSesionInvalida(fn) { alExpirar = fn; }

function indicarCarga(delta) {
  pendientes = Math.max(0, pendientes + delta);
  document.body.classList.toggle('cargando', pendientes > 0);
}

function codificarBasic(usuario, clave) {
  // UTF-8 -> base64 (btoa solo acepta Latin-1)
  const bytes = new TextEncoder().encode(`${usuario}:${clave}`);
  let binario = '';
  bytes.forEach((b) => { binario += String.fromCharCode(b); });
  return `Basic ${btoa(binario)}`;
}

const MENSAJES_ESTADO = {
  400: 'Los datos enviados no son válidos.',
  401: 'Usuario o contraseña incorrectos.',
  403: 'Tu rol no tiene permiso para realizar esta operación.',
  404: 'No se encontró lo que buscabas.',
  409: 'La operación entra en conflicto con el estado actual del inventario.',
  500: 'El servidor tuvo un error inesperado. Inténtalo de nuevo y, si continúa, avisa al administrador.',
};

async function solicitar(metodo, ruta, { cuerpo, consulta, encabezado } = {}) {
  const url = new URL(ruta, window.location.origin);
  if (consulta) {
    Object.entries(consulta).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '') url.searchParams.set(k, v);
    });
  }
  const cabeceras = { Accept: 'application/json' };
  const auth = encabezado || sesion?.encabezado;
  if (auth) cabeceras.Authorization = auth;
  if (cuerpo !== undefined) cabeceras['Content-Type'] = 'application/json';

  indicarCarga(1);
  let respuesta;
  try {
    respuesta = await fetch(url, {
      method: metodo,
      headers: cabeceras,
      body: cuerpo !== undefined ? JSON.stringify(cuerpo) : undefined,
      credentials: 'omit',
      cache: 'no-store',
    });
  } catch {
    throw new ApiError(0, 'No se pudo conectar con el servidor. Revisa tu conexión e inténtalo de nuevo.');
  } finally {
    indicarCarga(-1);
  }

  const tipo = respuesta.headers.get('content-type') || '';
  let datos = null;
  if (respuesta.status !== 204) {
    datos = tipo.includes('json') ? await respuesta.json().catch(() => null) : await respuesta.text();
  }

  if (!respuesta.ok) {
    // Estructura real del backend: { status, error, message, path, errores:[{campo,mensaje}] }
    const delServidor = datos && typeof datos === 'object' ? datos : {};
    let mensaje = delServidor.message || MENSAJES_ESTADO[respuesta.status]
      || `Error ${respuesta.status} del servidor.`;
    if (respuesta.status >= 500) mensaje = MENSAJES_ESTADO[500];   // nunca mostrar detalles internos
    if (respuesta.status === 401 && sesion && !encabezado) {
      sesion = null;
      alExpirar();
    }
    throw new ApiError(respuesta.status, mensaje, delServidor.errores || []);
  }
  return datos;
}

export const get = (ruta, consulta) => solicitar('GET', ruta, { consulta });
export const post = (ruta, cuerpo) => solicitar('POST', ruta, { cuerpo: cuerpo ?? undefined });
export const put = (ruta, cuerpo) => solicitar('PUT', ruta, { cuerpo });
export const patch = (ruta) => solicitar('PATCH', ruta);
export const del = (ruta) => solicitar('DELETE', ruta);

/** Comprueba las credenciales con GET /api/auth/me y abre la sesión. */
export async function iniciarSesion(usuario, clave) {
  const encabezado = codificarBasic(usuario, clave);
  const yo = await solicitar('GET', '/api/auth/me', { encabezado });
  sesion = { encabezado, usuario: yo };
  return yo;
}

export function cerrarSesion() { sesion = null; }
