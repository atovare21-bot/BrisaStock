-- ============================================================================
-- BrisaStock · Migración del modelo de datos v1 -> v2  (MariaDB / MySQL)
--
-- Qué cambia
--   1. usuarios se relaciona con ventas y con movimientos_inventario (llaves foráneas).
--   2. categorias pasa a ser una tabla propia (antes era texto repetido en productos).
--   3. detalle_ventas deja solo lo que no se puede obtener de otra tabla.
--   4. Tipos correctos: DECIMAL para dinero, DATE para la fecha de vencimiento,
--      longitudes reales y columnas obligatorias como NOT NULL.
--   5. Se eliminan columnas derivadas o sin uso (productos.estado, ventas.cliente).
--
-- Cómo usarlo (PowerShell, con MariaDB encendido)
--   1. RESPALDO:  mysqldump -u root --databases brisastock_db --result-file=respaldo.sql
--   2. Aplicar:   mysql -u root brisastock_db -e "source db/migracion_v2.sql"
--   3. Arrancar la aplicación.
--
-- Se ejecuta UNA sola vez sobre una base con el modelo v1. No borra ventas, productos,
-- movimientos ni usuarios. Una instalación nueva no lo necesita: la aplicación crea
-- las tablas del modelo v2 al arrancar.
-- ============================================================================

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- 1. usuarios: columnas obligatorias y longitudes reales
-- ---------------------------------------------------------------------------
UPDATE usuarios SET rol = 'USER' WHERE UPPER(rol) = 'EMPLEADO';          -- nombre antiguo del mismo rol
UPDATE usuarios SET nombre = username WHERE nombre IS NULL OR TRIM(nombre) = '';
UPDATE usuarios SET activo = 1 WHERE activo IS NULL;

ALTER TABLE usuarios
  MODIFY username VARCHAR(50)  NOT NULL,
  MODIFY nombre   VARCHAR(100) NOT NULL,
  MODIFY password VARCHAR(100) NOT NULL,
  MODIFY rol      VARCHAR(20)  NOT NULL,
  MODIFY activo   TINYINT(1)   NOT NULL DEFAULT 1;

-- Usuario al que se asignan los registros antiguos cuyo responsable no quedó guardado
SET @admin_id := (SELECT id FROM usuarios WHERE rol = 'ADMIN' ORDER BY id LIMIT 1);

-- ---------------------------------------------------------------------------
-- 2. categorias: tabla propia
-- ---------------------------------------------------------------------------
CREATE TABLE categorias (
  id     BIGINT      NOT NULL AUTO_INCREMENT,
  nombre VARCHAR(80) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_categorias_nombre UNIQUE (nombre)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Una fila por categoría. La comparación no distingue mayúsculas ni tildes, así que
-- "Lacteos" y "Lácteos" quedan como una sola categoría.
INSERT INTO categorias (nombre)
SELECT MAX(TRIM(categoria))
FROM productos
WHERE categoria IS NOT NULL AND TRIM(categoria) <> ''
GROUP BY TRIM(categoria);

UPDATE categorias SET nombre = 'Lácteos' WHERE nombre = 'Lacteos';        -- ortografía

-- ---------------------------------------------------------------------------
-- 3. productos
-- ---------------------------------------------------------------------------
ALTER TABLE productos
  ADD COLUMN categoria_id BIGINT NULL AFTER marca,
  ADD COLUMN fecha_vencimiento_nueva DATE NULL;

UPDATE productos p
JOIN categorias c ON c.nombre = TRIM(p.categoria)
SET p.categoria_id = c.id;

-- La fecha era texto libre: se convierte a DATE. Se aceptan AAAA-MM-DD y DD-MM-AAAA
-- (con guion o barra); cualquier otro texto queda sin fecha.
UPDATE productos SET fecha_vencimiento_nueva = CASE
  WHEN fecha_vencimiento REGEXP '^[0-9]{4}-[0-9]{2}-[0-9]{2}$'
    THEN STR_TO_DATE(fecha_vencimiento, '%Y-%m-%d')
  WHEN fecha_vencimiento REGEXP '^[0-9]{2}[-/][0-9]{2}[-/][0-9]{4}$'
    THEN STR_TO_DATE(REPLACE(fecha_vencimiento, '/', '-'), '%d-%m-%Y')
  ELSE NULL
END;

-- "estado" se elimina porque se calcula del stock; antes se conserva lo único que no
-- era derivable: el estado antiguo "Vencido" pasa a la marca manual de vencido.
UPDATE productos SET vencido = 1 WHERE estado = 'Vencido';
UPDATE productos SET vencido = 0 WHERE vencido IS NULL;
UPDATE productos SET stock = 0 WHERE stock IS NULL;
UPDATE productos SET stock_minimo = 0 WHERE stock_minimo IS NULL;

ALTER TABLE productos
  DROP COLUMN categoria,
  DROP COLUMN estado,
  DROP COLUMN fecha_vencimiento;

ALTER TABLE productos
  CHANGE fecha_vencimiento_nueva fecha_vencimiento DATE NULL,
  MODIFY nombre        VARCHAR(150)  NOT NULL,
  MODIFY marca         VARCHAR(100)  NULL,
  MODIFY descripcion   VARCHAR(255)  NULL,
  MODIFY precio_compra DECIMAL(12,2) NOT NULL,
  MODIFY precio_venta  DECIMAL(12,2) NOT NULL,
  MODIFY stock         INT           NOT NULL,
  MODIFY stock_minimo  INT           NOT NULL DEFAULT 0,
  MODIFY vencido       TINYINT(1)    NOT NULL DEFAULT 0,
  ADD CONSTRAINT fk_productos_categoria FOREIGN KEY (categoria_id) REFERENCES categorias (id);

-- ---------------------------------------------------------------------------
-- 4. ventas: quién la registró y quién la canceló
-- ---------------------------------------------------------------------------
ALTER TABLE ventas
  ADD COLUMN usuario_id BIGINT NULL AFTER fecha_venta,
  ADD COLUMN usuario_cancelacion_id BIGINT NULL AFTER fecha_cancelacion;

-- El vendedor se recupera del movimiento de inventario que dejó la venta
UPDATE ventas v
JOIN movimientos_inventario m ON m.tipo = 'VENTA' AND m.referencia = CONCAT('VENTA #', v.id)
JOIN usuarios u ON u.username = m.usuario
SET v.usuario_id = u.id;

-- Ventas anteriores al historial de movimientos: no guardaban responsable.
-- Se asignan al administrador para poder exigir la relación de aquí en adelante.
UPDATE ventas SET usuario_id = @admin_id WHERE usuario_id IS NULL;

UPDATE ventas v
JOIN usuarios u ON u.username = v.usuario_cancelacion
SET v.usuario_cancelacion_id = u.id;
UPDATE ventas SET usuario_cancelacion_id = @admin_id
WHERE estado = 'CANCELADA' AND usuario_cancelacion_id IS NULL;

ALTER TABLE ventas
  DROP COLUMN cliente,
  DROP COLUMN usuario_cancelacion;

ALTER TABLE ventas
  MODIFY fecha_venta DATETIME(6)   NOT NULL,
  MODIFY usuario_id  BIGINT        NOT NULL,
  MODIFY total       DECIMAL(12,2) NOT NULL,
  ADD CONSTRAINT fk_ventas_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id),
  ADD CONSTRAINT fk_ventas_usuario_cancelacion FOREIGN KEY (usuario_cancelacion_id) REFERENCES usuarios (id);

-- ---------------------------------------------------------------------------
-- 5. detalle_ventas: solo cantidad y precio del momento
--    (nombre, marca y categoría se leen del producto; el subtotal se calcula;
--     el stock antes/después está en movimientos_inventario)
-- ---------------------------------------------------------------------------
ALTER TABLE detalle_ventas
  DROP COLUMN nombre_producto,
  DROP COLUMN marca_producto,
  DROP COLUMN categoria_producto,
  DROP COLUMN stock_antes_venta,
  DROP COLUMN stock_despues_venta,
  DROP COLUMN subtotal;

ALTER TABLE detalle_ventas
  MODIFY venta_id        BIGINT        NOT NULL,
  MODIFY producto_id     BIGINT        NOT NULL,
  MODIFY cantidad        INT           NOT NULL,
  MODIFY precio_unitario DECIMAL(12,2) NOT NULL;

-- ---------------------------------------------------------------------------
-- 6. movimientos_inventario: el responsable pasa de texto a llave foránea
-- ---------------------------------------------------------------------------
ALTER TABLE movimientos_inventario
  ADD COLUMN usuario_id BIGINT NULL AFTER producto_id;

UPDATE movimientos_inventario m
JOIN usuarios u ON u.username = m.usuario
SET m.usuario_id = u.id;
UPDATE movimientos_inventario SET usuario_id = @admin_id WHERE usuario_id IS NULL;

ALTER TABLE movimientos_inventario
  DROP COLUMN usuario;

ALTER TABLE movimientos_inventario
  MODIFY usuario_id BIGINT NOT NULL,
  ADD CONSTRAINT fk_movimientos_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id);
