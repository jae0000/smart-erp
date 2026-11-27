package com.smarterp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:products;MODE=Oracle",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.schema-locations=classpath:product-test-schema.sql"
})
@AutoConfigureMockMvc
@Transactional
class ProductApiTest {
    private static final String PATH = "/api/products";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private ObjectNode product(String code) {
        return json.createObjectNode()
                .put("productCode", code).put("productName", "무선 마우스")
                .put("price", 25000).put("stockQty", 12);
    }

    private void createProduct(String code) throws Exception {
        mvc.perform(post(PATH).contentType(APPLICATION_JSON).content(product(code).toString()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true));
    }

    private JsonNode products() throws Exception {
        byte[] body = mvc.perform(get(PATH)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        return json.readTree(body).required("products");
    }

    @Test
    void recordsStockChangesAndRejectsAnExcessWithdrawal() throws Exception {
        createProduct("P001");
        long id = products().required(0).required("productId").longValue();
        String path = PATH + "/" + id + "/movements";

        mvc.perform(post(path).contentType(APPLICATION_JSON)
                        .content("{\"direction\":\"IN\",\"quantity\":2}"))
                .andExpect(status().isCreated());
        mvc.perform(post(path).contentType(APPLICATION_JSON)
                        .content("{\"direction\":\"OUT\",\"quantity\":3}"))
                .andExpect(status().isCreated());
        mvc.perform(post(path).contentType(APPLICATION_JSON)
                        .content("{\"direction\":\"OUT\",\"quantity\":12}"))
                .andExpect(status().isConflict());

        assertThat(products().required(0).required("stockQty").intValue()).isEqualTo(11);
        mvc.perform(get(PATH + "/movements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movements.length()").value(2))
                .andExpect(jsonPath("$.movements[0].direction").value("OUT"))
                .andExpect(jsonPath("$.movements[0].quantity").value(3))
                .andExpect(jsonPath("$.movements[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.movements[1].direction").value("IN"));
        mvc.perform(delete(PATH + "/{id}", id)).andExpect(status().isConflict());
    }

    @Test
    void readsUpdatesAndDeletesTheGeneratedId() throws Exception {
        assertThat(products().size()).isZero();
        createProduct("P001");
        JsonNode original = products().required(0);
        long id = original.required("productId").longValue();
        assertThat(id).isPositive();
        assertThat(original.required("productName").asText()).isEqualTo("무선 마우스");
        assertThat(original.required("price").textValue()).isEqualTo("25000.00");

        ObjectNode edit = product("P002").put("productName", "키보드").put("price", 123.45).put("stockQty", 0);
        mvc.perform(put(PATH + "/{id}", id).contentType(APPLICATION_JSON).content(edit.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        JsonNode updated = products().required(0);
        assertThat(updated.required("productId").longValue()).isEqualTo(id);
        assertThat(updated.required("productCode").asText()).isEqualTo("P002");
        assertThat(updated.required("productName").asText()).isEqualTo("키보드");
        assertThat(updated.required("price").textValue()).isEqualTo("123.45");
        assertThat(updated.required("stockQty").intValue()).isZero();

        mvc.perform(delete(PATH + "/{id}", id))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        assertThat(products().size()).isZero();
    }

    @Test
    void duplicateCodeReturnsConflictAndKeepsBothRowsInIdOrder() throws Exception {
        createProduct("Z");
        createProduct("A");
        JsonNode rows = products();
        assertThat(rows.required(0).required("productCode").asText()).isEqualTo("Z");
        long secondId = rows.required(1).required("productId").longValue();

        mvc.perform(post(PATH).contentType(APPLICATION_JSON).content(product("Z").toString()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").isNotEmpty());
        mvc.perform(put(PATH + "/{id}", secondId).contentType(APPLICATION_JSON).content(product("Z").toString()))
                .andExpect(status().isConflict());
        assertThat(products()).isEqualTo(rows);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"productCode\":\" \"}", "{\"productName\":null}", "{\"price\":-1}",
            "{\"price\":0.001}", "{\"price\":10000000000}", "{\"stockQty\":-1}",
            "{\"stockQty\":1.5}", "{\"stockQty\":2147483648}"
    })
    void invalidValuesReturn400WithoutCreatingARow(String changes) throws Exception {
        ObjectNode request = product("P001");
        request.setAll((ObjectNode) json.readTree(changes));
        mvc.perform(post(PATH).contentType(APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isNotEmpty());
        assertThat(products().size()).isZero();
    }

    @Test
    void distinguishesMissingProductsFromInvalidIds() throws Exception {
        mvc.perform(put(PATH + "/42").contentType(APPLICATION_JSON).content(product("P001").toString()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").isNotEmpty());
        mvc.perform(delete(PATH + "/42")).andExpect(status().isNotFound());
        mvc.perform(delete(PATH + "/abc")).andExpect(status().isBadRequest());
    }
}
