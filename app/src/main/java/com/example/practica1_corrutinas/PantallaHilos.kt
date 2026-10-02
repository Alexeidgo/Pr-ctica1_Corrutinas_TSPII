package com.example.practica1_corrutinas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

//Describimos los elementos de la interfaz con @Composable
@Composable
fun PantallaHilos(
    mainViewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    //observa el stateflow en viewmodel y lo convierte en estado de compose.
    val uiState by mainViewModel.uiState.collectAsState()

    //Calculamos cuántas veces se ejecutará el algoritmo
    val repeticiones = mainViewModel.n * uiState.multiplicador

    //organizamos la interfaz
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Actividad 2 y 3: Hilos",
            style = MaterialTheme.typography.headlineSmall
        )

        //añadimos la separación vertical entre los elementos
        Spacer(modifier = Modifier.height(20.dp))

        Text(text = "N = ${mainViewModel.n}")

        Text(text = "Selecciona el multiplicador:")

        //Organizamos horizontalmente los botones de N hasta 5N
        Row {
            for (valor in 1..5) {
                TextButton(
                    onClick = {
                        mainViewModel.seleccionarMultiplicador(valor)
                    },

                    //No permite cambiar la prueba mientras se ejecuta
                    enabled = !uiState.isBusy
                ) {
                    Text(text = "${valor}N")
                }
            }
        }

        //Muestra la opción seleccionada y el número de ejecuciones
        Text(
            text = "Prueba: ${uiState.multiplicador}N = " +
                    "$repeticiones ejecuciones"
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(text = uiState.statusMessage)

        //Muestra cuántas ejecuciones del algoritmo han terminado
        Text(
            text = "Terminadas: " +
                    "${uiState.ejecucionesTerminadas}/$repeticiones"
        )

        //El tiempo solo aparece cuando hay un resultado disponible
        uiState.tiempoMs?.let { tiempo ->
            Text(
                text = "Tiempo total: %.1f ms".format(tiempo),
                style = MaterialTheme.typography.titleLarge
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            //Ejecuta las repeticiones una después de otra
            //en el hilo principal
            onClick = {
                mainViewModel.iniciarSecuencial()
            },

            //Evita iniciar otra prueba mientras una está activa
            enabled = !uiState.isBusy
        ) {
            Text("Ejecutar secuencial")
        }

        Button(
            //Ejecuta las repeticiones en varios hilos secundarios
            onClick = {
                mainViewModel.iniciarConcurrente()
            },
            enabled = !uiState.isBusy
        ) {
            Text("Ejecutar concurrente")
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Actividad 3: Sincronización",
            style = MaterialTheme.typography.titleLarge
        )

        Button(
            //Provoca que los hilos sobrescriban sus incrementos
            onClick = {
                mainViewModel.iniciarSincronizacion(false)
            },
            enabled = !uiState.isBusy
        ) {
            Text("Sin sincronización")
        }

        Button(
            //Protege la lectura y escritura mediante synchronized
            onClick = {
                mainViewModel.iniciarSincronizacion(true)
            },
            enabled = !uiState.isBusy
        ) {
            Text("Con sincronización")
        }

        Button(
            //Solicita interrumpir la prueba y sus trabajadores
            onClick = {
                mainViewModel.cancelarSincronizacion()
            },
            enabled = uiState.pruebaSincronizacionActiva
        ) {
            Text("Cancelar prueba")
        }

//Muestra el valor esperado y el que realmente se registró
        uiState.datosEsperados?.let { esperado ->
            Text(text = "Incrementos esperados: $esperado")
        }

        uiState.datosRegistrados?.let { registrado ->
            Text(text = "Incrementos registrados: $registrado")
        }

        Text(
            text = "Contador manual: ${uiState.contadorManual}"
        )

        Button(
            //Permanece habilitado para comprobar si la interfaz
            //responde mientras se ejecuta la prueba
            onClick = {
                mainViewModel.aumentarContadorManual()
            }
        ) {
            Text("Sumar +1")
        }
    }
}