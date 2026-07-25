package com.mkisten.vacancybackend;

import com.mkisten.vacancybackend.client.AuthServiceClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        properties = {
                "spring.autoconfigure.exclude=" +
                        "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                        "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
        }
)
@AutoConfigureMockMvc
@ActiveProfiles("docker")
class ActuatorHealthEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthServiceClient authServiceClient;

    @Test
    void actuatorHealthIsPublic() throws Exception {
        mockMvc.perform(get("/api/actuator/health"))
                .andExpect(status().isOk());
    }
}
