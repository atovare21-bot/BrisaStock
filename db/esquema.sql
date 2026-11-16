-- ============================================================================
-- BrisaStock · Esquema de la base de datos (modelo v2) · MariaDB / MySQL
--
-- Archivo de REFERENCIA: es la estructura real de brisastock_db, generada con
-- mysqldump --no-data. No hace falta ejecutarlo: en una instalación nueva la
-- aplicación crea estas tablas al arrancar, y una base con el modelo anterior
-- se actualiza con db/migracion_v2.sql.
--
-- Relaciones
--   categorias 1 — N productos                (productos.categoria_id)
--   usuarios   1 — N ventas                   (ventas.usuario_id: quién la registró)
--   usuarios   1 — N ventas                   (ventas.usuario_cancelacion_id: quién la canceló)
--   usuarios   1 — N movimientos_inventario   (movimientos_inventario.usuario_id)
--   productos  1 — N movimientos_inventario   (movimientos_inventario.producto_id)
--   ventas     1 — N detalle_ventas           (detalle_ventas.venta_id)
--   productos  1 — N detalle_ventas           (detalle_ventas.producto_id)
-- ============================================================================

/*M!999999\- enable the sandbox mode */ 

CREATE TABLE `categorias` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `nombre` varchar(80) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_categorias_nombre` (`nombre`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `detalle_ventas` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `cantidad` int(11) NOT NULL,
  `precio_unitario` decimal(12,2) NOT NULL,
  `producto_id` bigint(20) NOT NULL,
  `venta_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKnunx2ycrr6jfyphwmmifo6lgx` (`producto_id`),
  KEY `FK2dn1sdhrbmva44wbs9fy6l7mn` (`venta_id`),
  CONSTRAINT `FK2dn1sdhrbmva44wbs9fy6l7mn` FOREIGN KEY (`venta_id`) REFERENCES `ventas` (`id`),
  CONSTRAINT `FKnunx2ycrr6jfyphwmmifo6lgx` FOREIGN KEY (`producto_id`) REFERENCES `productos` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `movimientos_inventario` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `cantidad` int(11) NOT NULL,
  `fecha` datetime(6) NOT NULL,
  `observacion` varchar(500) DEFAULT NULL,
  `referencia` varchar(120) DEFAULT NULL,
  `stock_anterior` int(11) NOT NULL,
  `stock_posterior` int(11) NOT NULL,
  `tipo` enum('AJUSTE','DEVOLUCION','ENTRADA','SALIDA','VENTA') NOT NULL,
  `producto_id` bigint(20) NOT NULL,
  `usuario_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_mov_producto_fecha` (`producto_id`,`fecha`),
  KEY `fk_movimientos_usuario` (`usuario_id`),
  CONSTRAINT `FK36oqksnam23hjdna6ny4oimsm` FOREIGN KEY (`producto_id`) REFERENCES `productos` (`id`),
  CONSTRAINT `fk_movimientos_usuario` FOREIGN KEY (`usuario_id`) REFERENCES `usuarios` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `productos` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `descripcion` varchar(255) DEFAULT NULL,
  `marca` varchar(100) DEFAULT NULL,
  `categoria_id` bigint(20) DEFAULT NULL,
  `nombre` varchar(150) NOT NULL,
  `precio_compra` decimal(12,2) NOT NULL,
  `precio_venta` decimal(12,2) NOT NULL,
  `stock` int(11) NOT NULL,
  `stock_minimo` int(11) NOT NULL DEFAULT 0,
  `vencido` tinyint(1) NOT NULL DEFAULT 0,
  `activo` tinyint(1) NOT NULL DEFAULT 1,
  `version` bigint(20) NOT NULL DEFAULT 0,
  `fecha_vencimiento` date DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_productos_categoria` (`categoria_id`),
  CONSTRAINT `fk_productos_categoria` FOREIGN KEY (`categoria_id`) REFERENCES `categorias` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `usuarios` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `activo` tinyint(1) NOT NULL DEFAULT 1,
  `nombre` varchar(100) NOT NULL,
  `password` varchar(100) NOT NULL,
  `rol` varchar(20) NOT NULL,
  `username` varchar(50) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKm2dvbwfge291euvmk6vkkocao` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `ventas` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `fecha_venta` datetime(6) NOT NULL,
  `usuario_id` bigint(20) NOT NULL,
  `total` decimal(12,2) NOT NULL,
  `estado` varchar(20) NOT NULL DEFAULT 'ACTIVA',
  `fecha_cancelacion` datetime(6) DEFAULT NULL,
  `usuario_cancelacion_id` bigint(20) DEFAULT NULL,
  `motivo_cancelacion` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_ventas_usuario` (`usuario_id`),
  KEY `fk_ventas_usuario_cancelacion` (`usuario_cancelacion_id`),
  CONSTRAINT `fk_ventas_usuario` FOREIGN KEY (`usuario_id`) REFERENCES `usuarios` (`id`),
  CONSTRAINT `fk_ventas_usuario_cancelacion` FOREIGN KEY (`usuario_cancelacion_id`) REFERENCES `usuarios` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
