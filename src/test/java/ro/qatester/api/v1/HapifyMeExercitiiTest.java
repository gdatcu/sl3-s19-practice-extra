package ro.qatester.api.v1;

import io.restassured.specification.RequestSpecification;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import ro.qatester.api.models.RegisterRequest;
import ro.qatester.api.v1.models.AuthData;

import java.io.File;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class HapifyMeExercitiiTest extends BaseApiTest {

    private static final long TS = System.currentTimeMillis();
    private String tokenAna, tokenBogdan, userAna;
    private int postId;

    private AuthData inregistreaza(String prenume, String email) {
        return anonim()
                .body(new RegisterRequest(prenume, "Tester" + TS, email, "Qa!Test2026"))
            .when().post("/auth/register")
            .then().spec(created)
            .extract().jsonPath().getObject("data", AuthData.class);
    }

    private int creeazaPostare(String text) {
        return ca(tokenAna).body(Map.of("body", text))
            .when().post("/posts")
            .then().spec(created)
            .extract().path("data.id");
    }

    @BeforeClass   // rulează DUPĂ initSpecs() din BaseApiTest (clasa părinte are prioritate)
    public void pregatireDate() {
        AuthData ana = inregistreaza("Ana", "ex_ana_" + TS + "@hapifyme.com");
        AuthData bogdan = inregistreaza("Bogdan", "ex_bogdan_" + TS + "@hapifyme.com");
        tokenAna = ana.getToken();
        userAna = ana.getUser().getUsername();
        tokenBogdan = bogdan.getToken();
        postId = creeazaPostare("Post pentru exercitii #qaschool" + TS);
    }

    @AfterClass(alwaysRun = true)   // plasa de siguranță: rulează chiar dacă testele pică
    public void curatenie() {
        ca(tokenAna).when().post("/users/close-account");
        ca(tokenBogdan).when().post("/users/close-account");
    }

    @Test
    public void testReactiiToggle() {
        // 1. Prima reacție "wow"
        ca(tokenBogdan).pathParam("id", postId).body(Map.of("reaction", "wow"))
            .when().post("/posts/{id}/react")
            .then().spec(ok)
                .body("data.reacted", equalTo(true))
                .body("data.user_reaction", equalTo("wow"));

        // 2. Filtrare după tipul reacției (query param)
        ca(tokenAna).pathParam("id", postId).queryParam("type", "wow")
            .when().get("/posts/{id}/reactions")
            .then().spec(ok)
                .body("data.summary.wow", equalTo(1))
                .body("data.reactors.reaction_type", everyItem(equalTo("wow")));

        ca(tokenAna).pathParam("id", postId).queryParam("type", "love")
            .when().get("/posts/{id}/reactions")
            .then().spec(ok).body("data.total_reactions", equalTo(0));

        // 3. Același "wow" încă o dată -> ANULEAZĂ reacția
        ca(tokenBogdan).pathParam("id", postId).body(Map.of("reaction", "wow"))
            .when().post("/posts/{id}/react")
            .then().spec(ok)
                .body("data.reacted", equalTo(false))
                .body("data.user_reaction", nullValue())
                .body("data.likes_count", equalTo(0));

        // 4. "like" apoi "love" -> se SCHIMBĂ tipul, contorul nu se dublează
        ca(tokenBogdan).pathParam("id", postId).body(Map.of("reaction", "like"))
            .when().post("/posts/{id}/react")
            .then().spec(ok).body("data.likes_count", equalTo(1));

        ca(tokenBogdan).pathParam("id", postId).body(Map.of("reaction", "love"))
            .when().post("/posts/{id}/react")
            .then().spec(ok)
                .body("data.user_reaction", equalTo("love"))
                .body("data.likes_count", equalTo(1));
    }

    @Test
    public void testPaginare() {
        creeazaPostare("A doua postare pentru paginare");   // Ana are acum 2 postări

        int idPagina1 = ca(tokenAna).pathParam("username", userAna)
                .queryParam("page", 1).queryParam("limit", 1)
            .when().get("/users/{username}/posts")
            .then().spec(ok)
                .body("data", hasSize(1))
                .body("meta.page", equalTo(1))
                .body("meta.has_more", equalTo(true))
                .extract().path("data[0].id");

        int idPagina2 = ca(tokenAna).pathParam("username", userAna)
                .queryParam("page", 2).queryParam("limit", 1)
            .when().get("/users/{username}/posts")
            .then().spec(ok)
                .body("data", hasSize(1))
                .body("meta.page", equalTo(2))
                .extract().path("data[0].id");

        assertThat("Paginile nu trebuie să se suprapună", idPagina2, not(equalTo(idPagina1)));
    }

    @Test
    public void testUploadMultipart() {
        File imagine = new File("src/test/resources/pixel.png");

        // NU folosim requestSpec: acesta forțează Content-Type JSON.
        // Pentru multipart, REST Assured setează singur "multipart/form-data; boundary=..."
        given()
                .baseUri(BASE_URI)
                .auth().oauth2(tokenAna)
                .multiPart("file", imagine, "image/png")
                .multiPart("folder", "posts")
            .when()
                .post("/upload/media")
            .then()
                .statusCode(201)
                .body("success", equalTo(true))
                .body("data.mime_type", equalTo("image/png"))
                .body("data.size", greaterThan(0))
                .body("data.file_path", startsWith("assets/images/"));
    }

    @DataProvider(name = "operatiiDoarPentruAutor")
    public Object[][] operatiiDoarPentruAutor() {
        return new Object[][] {
            // metodă, endpoint, body, fragment din mesaj
            { "PUT",  "/posts/{id}",       Map.of("body", "incerc sa editez"), "only edit your own posts" },
            { "GET",  "/posts/{id}/views", null,                               "only view detailed" },
            { "POST", "/posts/{id}/pin",   null,                               "only pin your own" }
        };
    }

    @Test(dataProvider = "operatiiDoarPentruAutor")
    public void testBogdanNuAreAcces(String metoda, String endpoint, Object body, String mesaj) {
        RequestSpecification cerere = ca(tokenBogdan).pathParam("id", postId);   // Bogdan, pe postarea Anei
        if (body != null) {
            cerere.body(body);
        }

        cerere
            .when().request(metoda, endpoint)
            .then()
                .spec(eroare(403))
                .body("message", containsString(mesaj));
    }
}
