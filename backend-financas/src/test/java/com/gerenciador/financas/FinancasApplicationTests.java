package com.gerenciador.financas;

import org.junit.jupiter.api.Test;

// Herda a configuração dos testes de integração (PostgreSQL no Testcontainers),
// então também não depende mais do banco local.
class FinancasApplicationTests extends IntegracaoTestBase {

	@Test
	void contextLoads() {
	}

}
