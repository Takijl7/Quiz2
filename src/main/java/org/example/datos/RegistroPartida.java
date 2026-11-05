package org.example.datos;

import java.time.LocalDateTime;

public record RegistroPartida(LocalDateTime inicio, LocalDateTime fin, String resultado, int idBlanco, int idNegro, int puntosBlanco, int puntosNegro, String resultadoBlanco, String resultadoNegro, int piezasBlanco, int piezasNegro) { }