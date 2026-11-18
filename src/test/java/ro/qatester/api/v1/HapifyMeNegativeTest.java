package ro.qatester.api.v1;

import io.restassured.specification.RequestSpecification;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import ro.qatester.api.models.RegisterRequest;

import java.util.Map;

import static org.hamcrest.Matchers.*;

public class HapifyMeNegativeTest extends BaseApiTest {

    @DataProvider(name = "eroriPublice")
    public Object[][] eroriPublice() {
        return new Object[][] {
            // metodă, endpoint, body (null = fără body), status așteptat, fragment din message
            { "GET",  "/auth/me",             null,     401, "Missing Bearer token" },
            { "POST", "/auth/refresh",        null,     401, "Missing Bearer token" },
            { "POST", "/auth/login",          Map.of(), 422, "Email or username and password are required" },
            { "POST", "/auth/login",          Map.of("log_email", "user_inexistent_qa", "log_password", "x"),
                                                        401, "Invalid credentials" },
            { "POST", "/auth/register",       Map.of(), 422, "Validation failed" },
            { "POST", "/auth/reset-password", Map.of("token", "abc", "new_password", "Parola123!"),
                                                        400, "Invalid or expired" },
            { "GET",  "/links/preview",       null,     422, "\"url\" is required" }
        };
    }

    @Test(dataProvider = "eroriPublice")
    public void testEroriEndpointuriPublice(String metoda, String endpoint, Object body,
                                            int status, String mesaj) {
        RequestSpecification cerere = anonim();
        if (body != null) {
            cerere.body(body);
        }

        cerere
            .when()
                .request(metoda, endpoint)      // metoda HTTP vine din DataProvider
            .then()
                .spec(eroare(status))
                .body("message", containsString(mesaj));
    }

    @Test(description = "422 - validare pe fiecare câmp în parte")
    public void testValidareInregistrare() {
        anonim()
                .body(new RegisterRequest("", "", "email-invalid", "1"))
            .when()
                .post("/auth/register")
            .then()
                .spec(eroare(422))
                .body("message", equalTo("Validation failed"))
                .body("errors.first_name", equalTo("First name must be between 2 and 25 characters"))
                .body("errors.last_name", equalTo("Last name must be between 2 and 25 characters"))
                .body("errors.email", equalTo("Invalid email address"))
                .body("errors.password", equalTo("Password must be at least 5 characters long"));
    }
}
