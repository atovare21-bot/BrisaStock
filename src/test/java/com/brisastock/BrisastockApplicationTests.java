package com.brisastock;

import com.brisastock.support.SoloBdAislada;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ContextConfiguration(initializers = SoloBdAislada.class)
class BrisastockApplicationTests {

	@Test
	void contextLoads() {
	}

}
