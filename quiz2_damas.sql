CREATE DATABASE IF NOT EXISTS quiz2_damas
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE quiz2_damas;

CREATE TABLE IF NOT EXISTS jugadores (
                                         id_jugador     INT AUTO_INCREMENT PRIMARY KEY,
                                         usuario        VARCHAR(50) NOT NULL UNIQUE,   -- el usuario es UNIQUE por jugador
    puntos_totales INT  NOT NULL DEFAULT 0,
    fecha_registro TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    ) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS partidas (
                                        id_partida   INT AUTO_INCREMENT PRIMARY KEY,
                                        fecha_inicio DATETIME NOT NULL,
                                        fecha_fin    DATETIME NOT NULL,
                                        resultado    VARCHAR(16) NOT NULL,  -- 'VICTORIA_BLANCO' | 'VICTORIA_NEGRO' | 'EMPATE'
    lado_blanco  CHAR(1) NOT NULL,      -- id del jugador con bando blanco
    lado_negro   CHAR(1) NOT NULL       -- id del jugador con bando negro
    ) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS participaciones (
                                               id_partida         INT NOT NULL,
                                               id_jugador        INT NOT NULL,
                                               bando             CHAR(1) NOT NULL,       -- 'BLANCO' o 'NEGRO' en esta partida
    puntos            INT NOT NULL DEFAULT 0, -- 5 si gano, 0 si perdio o empató
    resultado         VARCHAR(12) NOT NULL,   -- 'VICTORIA' | 'DERROTA' | 'EMPATE'
    piezas_restantes  INT NOT NULL DEFAULT 0, -- fichas que le quedan al finalizar
    PRIMARY KEY (id_partida, id_jugador),
    CONSTRAINT fk_part FOREIGN KEY (id_partida)  REFERENCES partidas(id_partida),
    CONSTRAINT fk_jug  FOREIGN KEY (id_jugador) REFERENCES jugadores(id_jugador)
    ) ENGINE=InnoDB;
