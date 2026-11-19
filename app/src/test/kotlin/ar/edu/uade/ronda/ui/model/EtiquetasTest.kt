package ar.edu.uade.ronda.ui.model

import ar.edu.uade.ronda.core.model.AlertType
import ar.edu.uade.ronda.core.model.CommandType
import ar.edu.uade.ronda.core.model.IncidentType
import ar.edu.uade.ronda.data.local.EventKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class EtiquetasTest {
    private fun legible(texto: String, valor: Enum<*>) {
        assertFalse(texto.isBlank(), "${valor.name} sin etiqueta")
        assertFalse(texto.contains('_') || texto == texto.uppercase(), "${valor.name} se muestra crudo: $texto")
    }

    @Test fun todosLosTiposDeAlertaTienenEtiqueta() =
        AlertType.entries.forEach { legible(Etiquetas.alerta(it), it); legible(Etiquetas.registro(EventKind.ALERTA.name, it.name), it) }

    @Test fun todosLosTiposDeIncidenteTienenEtiqueta() =
        IncidentType.entries.forEach { legible(Etiquetas.incidente(it), it); legible(Etiquetas.registro(EventKind.INCIDENTE.name, it.name), it) }

    @Test fun todasLasOrdenesTienenEtiqueta() =
        CommandType.entries.forEach { legible(Etiquetas.comando(it), it); legible(Etiquetas.registro(EventKind.COMANDO.name, it.name), it) }

    @Test fun tipoDesconocidoNoMuestraElNombreCrudo() =
        assertEquals("Evento", Etiquetas.registro(EventKind.ALERTA.name, "NUEVO_TIPO"))
}
