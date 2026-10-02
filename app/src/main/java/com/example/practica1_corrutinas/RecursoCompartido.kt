package com.example.practica1_corrutinas

import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicReference

//Representa el dato compartido al que accederán varios hilos
class RecursoCompartido {

    //Volatile permite que los hilos vean los cambios del valor
    @Volatile
    private var valor = 0

    //Todos los trabajadores utilizan el mismo objeto para sincronizarse
    private val bloqueo = Any()

    fun ejecutarPrueba(
        repeticiones: Int,
        sincronizar: Boolean,
        algoritmo: () -> Unit
    ): Int {
        val trabajadores = mutableListOf<Thread>()

        //La barrera obliga a todos a leer antes de que alguien escriba.
        //Solo se utiliza para demostrar el error sin sincronización.
        val barrera = CyclicBarrier(repeticiones)

        //Guarda un posible error de los trabajadores de forma segura
        val error = AtomicReference<Exception?>(null)

        try {
            repeat(repeticiones) {
                val trabajador = Thread {
                    try {
                        //Ejecutamos el algoritmo que ya usamos antes
                        algoritmo()

                        if (sincronizar) {
                            incrementarConSincronizacion()
                        } else {
                            incrementarSinSincronizacion(barrera)
                        }

                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        error.compareAndSet(null, e)

                    } catch (e: Exception) {
                        error.compareAndSet(null, e)

                        //Libera a los hilos que pudieran estar esperando en la barrera si ocurre un error
                        barrera.reset()
                    }
                }

                trabajadores.add(trabajador)
            }

            //Iniciamos todos los hilos antes de esperar sus resultados
            trabajadores.forEach {
                it.start()
            }

            //Est función se ejecuta desde el coordinador, por lo que join no bloquea la interfaz
            trabajadores.forEach {
                it.join()
            }

            error.get()?.let {
                throw it
            }

            return valor

        } finally {
            //Si se cancela el coordinador, solicita detener
            //los trabajadores que todavía estén activos
            trabajadores.forEach {
                it.interrupt()
            }
        }
    }

    private fun incrementarSinSincronizacion(
        barrera: CyclicBarrier
    ) {
        //Varios hilos leen el mismo valor antes de actualizarlo
        val anterior = valor

        //Esperamos a que todos hayan leído ese valor
        barrera.await()

        //Simulamos el tiempo que ocupa el acceso al recurso
        Thread.sleep(100)

        //Los hilos escriben el mismo resultado y se pierden incrementos
        valor = anterior + 1
    }

    private fun incrementarConSincronizacion() {
        //Solo un hilo puede ejecutar esta sección a la vez
        synchronized(bloqueo) {

            //Un hilo que esperaba el bloqueo puede haber sido cancelado
            if (Thread.currentThread().isInterrupted) {
                throw InterruptedException()
            }

            val anterior = valor

            Thread.sleep(100)

            //Protegemos toda la operación: lectura y escritura
            valor = anterior + 1
        }
    }
}