package com.cubrid.tools.ideaconfig.producer;

import com.cubrid.tools.ideaconfig.model.Bundle;
import com.cubrid.tools.ideaconfig.model.Product;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RunConfigProducerTest {

    @TempDir
    Path runConfigDir;

    @Test
    void singleProductAndConsoleAppKeepThePlainNames() throws IOException {
        producer().generateAll(
                List.of(product("com.example.desktop")),
                List.of(bundle("com.example.app"), consoleApp("com.example.command")));

        assertThat(runConfigDir.resolve("CMT_Desktop.xml")).exists();
        assertThat(runConfigDir.resolve("CMT_Console.xml")).exists();
    }

    @Test
    void severalProductsGetOneFileEach() throws IOException {
        producer().generateAll(
                List.of(product("com.example.desktop"), product("com.example.server")),
                List.of(bundle("com.example.app")));

        assertThat(runConfigDir.resolve("CMT_Desktop__com.example.desktop_.xml")).exists();
        assertThat(runConfigDir.resolve("CMT_Desktop__com.example.server_.xml")).exists();
    }

    @Test
    void severalConsoleAppsGetOneFileEach() throws IOException {
        producer().generateAll(
                List.of(product("com.example.desktop")),
                List.of(consoleApp("com.example.command"), consoleApp("com.example.tool")));

        assertThat(runConfigDir.resolve("CMT_Console__com.example.command_.xml")).exists();
        assertThat(runConfigDir.resolve("CMT_Console__com.example.tool_.xml")).exists();
    }

    private RunConfigProducer producer() {
        return new RunConfigProducer(runConfigDir, null);
    }

    private static Product product(String id) {
        Product product = new Product(id, id, Path.of("/tmp", id));
        product.setApplication(id + ".application");
        return product;
    }

    private static Bundle bundle(String symbolicName) {
        return new Bundle(symbolicName, "1.0.0", Path.of("/tmp", symbolicName));
    }

    private static Bundle consoleApp(String symbolicName) {
        Bundle bundle = bundle(symbolicName);
        bundle.setMainClass(symbolicName + ".Main");
        return bundle;
    }
}
