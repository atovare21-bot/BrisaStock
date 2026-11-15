// Usuarios (solo ADMIN): GET /api/usuarios y POST /api/usuarios/admin.
// El backend no ofrece editar usuarios ni cambiar roles, así que la interfaz tampoco.
import * as api from '../api.js';
import {
  h, vaciar, tabla, campo, dialogo, aviso, mensaje, ocupado, mostrarErrorCampo, limpiarErrores, panelError,
} from '../ui.js';

export async function render(raiz) {
  const zona = h('div', { 'aria-live': 'polite' });
  raiz.append(
    h('div', { class: 'herramientas' }, h('div', { class: 'crece' }),
      h('button', { class: 'btn btn--primario', type: 'button', onclick: () => nuevoUsuario(cargar) }, 'Nuevo usuario')),
    zona);

  async function cargar() {
    try {
      const usuarios = await api.get('/api/usuarios');
      vaciar(zona).append(h('section', { class: 'seccion' },
        tabla([
          { titulo: 'Nombre', principal: true, celda: (u) => u.nombre },
          { titulo: 'Usuario', celda: (u) => u.username },
          { titulo: 'Rol', celda: (u) => h('span', { class: `insignia ${u.rol === 'ADMIN' ? 'insignia--info' : 'insignia--neutro'}` }, u.rol === 'ADMIN' ? 'Administrador' : 'Usuario') },
          { titulo: 'Estado', celda: (u) => h('span', { class: `insignia ${u.activo ? 'insignia--ok' : 'insignia--error'}` }, u.activo ? 'Activo' : 'Desactivado') },
        ], usuarios, { vacioTitulo: 'Sin usuarios' })));
    } catch (e) {
      vaciar(zona).append(panelError(e, cargar));
    }
  }
  await cargar();
}

function nuevoUsuario(alGuardar) {
  const nombre = h('input', { maxlength: 100, required: true });
  const username = h('input', { maxlength: 50, autocomplete: 'off', autocapitalize: 'none', spellcheck: 'false', required: true });
  const password = h('input', { type: 'password', maxlength: 72, autocomplete: 'new-password', required: true });
  const rol = h('select', {}, h('option', { value: 'USER' }, 'Usuario: vende y registra entradas'), h('option', { value: 'ADMIN' }, 'Administrador: acceso completo'));
  const activo = h('input', { type: 'checkbox', checked: true, id: 'ch-activo' });
  const errorGeneral = h('div', {});
  const form = h('form', { id: 'form-usuario', novalidate: true },
    errorGeneral,
    campo('Nombre', nombre, { nombre: 'nombre' }),
    campo('Usuario', username, { nombre: 'username', ayuda: '3 a 50 caracteres: letras, números, punto, guion y guion bajo.' }),
    campo('Contraseña', password, { nombre: 'password', ayuda: 'Mínimo 8 caracteres.' }),
    campo('Rol', rol, { nombre: 'rol' }),
    h('div', { class: 'campo' }, h('div', { class: 'casilla' }, activo, h('label', { for: 'ch-activo' }, 'Usuario activo'))));
  const guardar = h('button', { class: 'btn btn--primario', type: 'submit', form: 'form-usuario' }, 'Crear usuario');
  const d = dialogo({ titulo: 'Nuevo usuario', cuerpo: form, acciones: [h('button', { class: 'btn', type: 'button', onclick: () => d.cerrar() }, 'Cancelar'), guardar] });

  form.addEventListener('submit', async (ev) => {
    ev.preventDefault();
    limpiarErrores(form);
    vaciar(errorGeneral);
    const errores = [];
    if (!nombre.value.trim()) errores.push(['nombre', 'El nombre es obligatorio.']);
    if (!/^[A-Za-z0-9._-]{3,50}$/.test(username.value.trim())) errores.push(['username', 'Usa de 3 a 50 letras, números, punto, guion o guion bajo.']);
    if (password.value.length < 8) errores.push(['password', 'La contraseña debe tener al menos 8 caracteres.']);
    if (errores.length) { errores.forEach(([k, m]) => mostrarErrorCampo(form, k, m)); return; }
    await ocupado(guardar, async () => {
      try {
        await api.post('/api/usuarios/admin', {
          nombre: nombre.value.trim(), username: username.value.trim(), password: password.value,
          rol: rol.value, activo: activo.checked,
        });
        password.value = '';
        aviso('Usuario creado.', 'ok');
        d.cerrar();
        alGuardar();
      } catch (e) {
        const sinCampo = [];
        (e.errores || []).forEach((x) => { if (!mostrarErrorCampo(form, x.campo, x.mensaje)) sinCampo.push(x); });
        errorGeneral.append(mensaje('error', e.errores?.length ? 'Revisa los campos marcados.' : e.message));
      }
    });
  });
  nombre.focus();
}
