package com.brisastock.api;

import com.brisastock.dto.MovimientoStockRequestDTO;
import com.brisastock.model.Producto;
import com.brisastock.service.InventarioService;
import com.brisastock.support.PruebaApiBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Comportamiento ante modificaciones simultáneas del mismo producto (@Version). */
class ConcurrenciaInventarioTest extends PruebaApiBase {

    @Autowired
    private InventarioService inventarioService;

    @Test
    void actualizacionConVersionVieja_esRechazada_yNoPisaElStock() {
        Producto p = crearProducto("Cola", 10, 1000.0, false);

        // Dos "usuarios" leen el mismo producto (misma versión)
        Producto copiaA = productoRepository.findById(p.getId()).orElseThrow();
        Producto copiaB = productoRepository.findById(p.getId()).orElseThrow();

        copiaA.setStock(11);
        productoRepository.save(copiaA);          // el primero guarda: sube la versión

        copiaB.setStock(15);                       // el segundo trabaja con datos viejos
        assertThrows(OptimisticLockingFailureException.class,
                () -> productoRepository.save(copiaB));

        assertEquals(11, stockActual(p.getId()));  // no se perdió el cambio del primero
    }

    @Test
    void entradasSimultaneas_stockYMovimientosQuedanConsistentes() throws Exception {
        Producto p = crearProducto("Cola", 100, 1000.0, false);
        int hilos = 8;

        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        CountDownLatch listos = new CountDownLatch(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        AtomicInteger exitosas = new AtomicInteger();
        AtomicInteger conflictos = new AtomicInteger();
        List<Throwable> inesperados = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < hilos; i++) {
            pool.submit(() -> {
                listos.countDown();
                try {
                    salida.await();
                    inventarioService.registrarEntrada(
                            new MovimientoStockRequestDTO(p.getId(), 1, null, null));
                    exitosas.incrementAndGet();
                } catch (ConcurrencyFailureException e) {
                    conflictos.incrementAndGet();   // conflicto detectado: se descartó completo
                } catch (Throwable e) {
                    inesperados.add(e);
                }
            });
        }
        listos.await();
        salida.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        assertTrue(inesperados.isEmpty(), "Errores inesperados: " + inesperados);
        assertEquals(hilos, exitosas.get() + conflictos.get());
        assertTrue(exitosas.get() >= 1);

        // Invariante clave: cada entrada confirmada sumó exactamente 1 y dejó 1 movimiento;
        // ninguna actualización se perdió.
        assertEquals(100 + exitosas.get(), stockActual(p.getId()));
        assertEquals(exitosas.get(), movimientoRepository.count());
    }
}
