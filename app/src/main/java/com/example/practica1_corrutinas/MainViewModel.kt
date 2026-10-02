package com.example.practica1_corrutinas

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

//Aquí pusimos todos los datos que va a mostrar la pantalla
data class UiState(
    val statusMessage: String = "Sistema listo",
    val contadorManual: Int = 0,
    val multiplicador: Int = 1,
    val ejecucionesTerminadas: Int = 0,
    val tiempoMs: Double? = null,
    val isBusy: Boolean = false,

    //Datos para mostrar el resultado de la sincronización
    val datosEsperados: Int? = null,
    val datosRegistrados: Int? = null,
    val pruebaSincronizacionActiva: Boolean = false
)

//Contiene la lógica que implementamos.
class MainViewModel : ViewModel() {

    //Es el estado modificable en view model
    private val _uiState = MutableStateFlow(UiState())

    //La interfaz puede ver el estado pero no modifica directamente
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    //Valor base: las pruebas tendrán 2, 4, 6, 8 y 10 ejecuciones
    val n = 2

    //guarda la referencia al hilo secundario que coordina la prueba
    private var hiloCoordinador: Thread? = null

    //Selecciona si ejecutaremos N, 2N, 3N, 4N o 5N veces el algoritmo
    fun seleccionarMultiplicador(valor: Int) {
        if (_uiState.value.isBusy) return
        if (valor !in 1..5) return

        _uiState.update {
            it.copy(
                multiplicador = valor,
                ejecucionesTerminadas = 0,
                tiempoMs = null,
                statusMessage = "Sistema listo",
                datosEsperados = null,
                datosRegistrados = null
            )
        }
    }

    // Ejecuta el contador en el hilo principal, bloqueando cualquier otra acción
    fun iniciarSecuencial() {
        if (_uiState.value.isBusy) return

        val repeticiones = n * _uiState.value.multiplicador

        prepararPrueba("Ejecutando en el hilo principal...")

        //Guardamos el instante en que comienza la prueba
        val inicio = System.nanoTime()

        //Cada ejecución termina antes de comenzar la siguiente
        repeat(repeticiones) {
            contar()
            registrarEjecucionTerminada()
        }

        finalizarPrueba(inicio, "secuencial")
    }

    //Ejecuta varias copias del mismo contador en hilos secundarios
    fun iniciarConcurrente() {
        if (_uiState.value.isBusy) return

        val repeticiones = n * _uiState.value.multiplicador

        prepararPrueba("Ejecutando en hilos secundarios...")

        //Se crea un hilo secundario para coordinar la prueba,
        //el hilo principal queda disponible para atender la interfaz
        hiloCoordinador = Thread {

            //Guarda los hilos que ejecutarán cada contador
            val trabajadores = mutableListOf<Thread>()

            try {
                val inicio = System.nanoTime()

                //Creamos un hilo por cada repetición del algoritmo
                repeat(repeticiones) {
                    val trabajador = Thread {
                        try {
                            contar()
                            registrarEjecucionTerminada()
                        } catch (e: InterruptedException) {
                            //Restablecemos la señal de interrupción
                            //y dejamos de ejecutar este contador
                            Thread.currentThread().interrupt()
                        }
                    }

                    trabajadores.add(trabajador)
                }

                //Inicia la ejecución de todos los hilos
                trabajadores.forEach {
                    it.start()
                }

                //El coordinador espera a que todos los hilos terminen
                //Esta espera no bloquea el hilo principal
                trabajadores.forEach {
                    it.join()
                }

                //Medimos el tiempo después de que todos hayan terminado
                finalizarPrueba(inicio, "concurrente")

            } catch (e: InterruptedException) {
                //Si el coordinador es interrumpido, detenemos la prueba
                Thread.currentThread().interrupt()

                _uiState.update {
                    it.copy(
                        statusMessage = "Prueba interrumpida",
                        isBusy = false
                    )
                }

            } finally {
                //Solicitamos interrumpir cualquier trabajador
                //que todavía siga activo al salir de la prueba
                trabajadores.forEach {
                    it.interrupt()
                }
            }
        }

        //Inicia la ejecución del hilo coordinador
        hiloCoordinador?.start()
    }

    //Función para cambiar los campos indicados
    private fun prepararPrueba(mensaje: String) {
        _uiState.update {
            it.copy(
                statusMessage = mensaje,
                ejecucionesTerminadas = 0,
                tiempoMs = null,
                isBusy = true,
                datosEsperados = null,
                datosRegistrados = null,
                pruebaSincronizacionActiva = false
            )
        }
    }

    // Algoritmo compartido por ambos modos.
    private fun contar() {
        for (i in 1..4) {
            //Se bloquea 100 milisegundos el hilo que se ejecuta
            //en secuencial bloquea el principal
            //en concurrente bloquea el trabajador secundario
            Thread.sleep(100)
        }
    }

    //Actualiza cuántas ejecuciones completas han terminado
    private fun registrarEjecucionTerminada() {
        //update permite incrementar el valor de forma atómica,
        //aunque varios hilos terminen al mismo tiempo
        _uiState.update {
            it.copy(
                ejecucionesTerminadas = it.ejecucionesTerminadas + 1
            )
        }
    }

    //Indica cuando terminó la tarea y permite iniciar otra ejecución.
    private fun finalizarPrueba(inicio: Long, modo: String) {
        //Calculamos el tiempo transcurrido en nanosegundos
        //y lo convertimos a milisegundos
        val tiempo = (System.nanoTime() - inicio) / 1_000_000.0

        _uiState.update {
            it.copy(
                statusMessage = "Prueba $modo terminada",
                tiempoMs = tiempo,
                isBusy = false
            )
        }
    }

    //Ejecuta la prueba de acceso al dato compartido
    fun iniciarSincronizacion(sincronizar: Boolean) {
        if (_uiState.value.isBusy) return

        val repeticiones = n * _uiState.value.multiplicador

        val modo = if (sincronizar) {
            "con sincronización"
        } else {
            "sin sincronización"
        }

        prepararPrueba("Ejecutando $modo...")

        _uiState.update {
            it.copy(
                datosEsperados = repeticiones,
                pruebaSincronizacionActiva = true
            )
        }

        //El coordinador ejecuta la prueba sin bloquear la interfaz
        hiloCoordinador = Thread {
            try {
                val inicio = System.nanoTime()

                //Cada prueba utiliza un recurso nuevo que empieza en cero
                val recurso = RecursoCompartido()

                //Cada trabajador cuenta y luego escribe en el recurso
                val resultado = recurso.ejecutarPrueba(
                    repeticiones = repeticiones,
                    sincronizar = sincronizar,
                    algoritmo = { contar() }
                )

                val tiempo =
                    (System.nanoTime() - inicio) / 1_000_000.0

                val mensaje = if (resultado == repeticiones) {
                    "Resultado correcto: no se perdieron incrementos"
                } else {
                    "Condición de carrera: se perdieron " +
                            "${repeticiones - resultado} incrementos"
                }

                _uiState.update {
                    it.copy(
                        statusMessage = mensaje,
                        ejecucionesTerminadas = repeticiones,
                        datosRegistrados = resultado,
                        tiempoMs = tiempo,
                        isBusy = false,
                        pruebaSincronizacionActiva = false
                    )
                }

            } catch (e: InterruptedException) {
                //La cancelación sale de la prueba y solicita detener
                //los trabajadores mediante el finally del recurso
                Thread.currentThread().interrupt()

                _uiState.update {
                    it.copy(
                        statusMessage =
                            "Prueba cancelada: resultado incompleto",
                        datosRegistrados = null,
                        tiempoMs = null,
                        isBusy = false,
                        pruebaSincronizacionActiva = false
                    )
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        statusMessage =
                            "Error en la prueba: ${e.message}",
                        isBusy = false,
                        pruebaSincronizacionActiva = false
                    )
                }
            }
        }

        hiloCoordinador?.start()
    }

    //Cancela únicamente la prueba de sincronización que está activa
    fun cancelarSincronizacion() {
        if (!_uiState.value.pruebaSincronizacionActiva) return

        //El coordinador atenderá esta señal al esperar con join
        hiloCoordinador?.interrupt()
    }

    //función para comprobar si la interfaz responde durante el conteo
    fun aumentarContadorManual() {
        _uiState.update {
            it.copy(contadorManual = it.contadorManual + 1)
        }
    }

    //al liberar viewmodel, se solicita interrumpir el hilo que este trabajando
    // para evitar que siga trabajando sin ser necesario
    override fun onCleared() {
        //Al interrumpir el coordinador, su bloque finally
        //también solicita interrumpir a los trabajadores
        hiloCoordinador?.interrupt()
        super.onCleared()
    }
}