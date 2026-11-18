package ro.qatester.api.v1;

import org.testng.annotations.Test;
import ro.qatester.api.models.RegisterRequest;
import ro.qatester.api.v1.models.*;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.withArgs;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class HapifyMeE2EFlowTest extends BaseApiTest {

    // ---------- Date de test UNICE pentru fiecare rulare ----------
    private static final long TS = System.currentTimeMillis();
    private static final String PAROLA = "Qa!Test2026";
    private static final String EMAIL_ANA = "ana_" + TS + "@hapifyme.com";
    private static final String EMAIL_BOGDAN = "bogdan_" + TS + "@hapifyme.com";
    private static final String HASHTAG = "qaschool" + TS;
    private static final String DEVICE_TOKEN = "fcm_qa_" + TS;

    // Imagine PNG de 1x1 pixeli, codată Base64 - suficientă pentru testele de upload
    private static final String PIXEL_PNG = "data:image/png;base64,"
            + "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";

    // ---------- "Ștafeta": valori extrase și transmise între etape ----------
    private String tokenAna, tokenBogdan;
    private String userAna, userBogdan;
    private String imagePath;
    private int postId, repostId, commentId, messageId, pollOptionId;

    @Test(description = "GET /health - API-ul și baza de date sunt funcționale")
    public void etapa00_healthCheck() {
        anonim()
        .when()
            .get("/health")
        .then()
            .spec(ok)
            .body("data.status", equalTo("healthy"))
            .body("data.database", equalTo("connected"))
            .time(lessThan(5000L));                 // timp de răspuns sub 5 secunde
    }

    @Test(dependsOnMethods = "etapa00_healthCheck",
          description = "POST /auth/register x2 - creăm cei doi actori ai fluxului")
    public void etapa01_inregistrare() {
        AuthData ana = anonim()
                .body(new RegisterRequest("Ana", "Tester" + TS, EMAIL_ANA, PAROLA))
            .when()
                .post("/auth/register")
            .then()
                .spec(created)
                .body("data.token_type", equalTo("Bearer"))
                .body("data.expires_in", equalTo(2592000))      // 30 de zile, în secunde
                .body("data.user.email", equalTo(EMAIL_ANA))
                .extract().jsonPath().getObject("data", AuthData.class);

        AuthData bogdan = anonim()
                .body(new RegisterRequest("Bogdan", "Tester" + TS, EMAIL_BOGDAN, PAROLA))
            .when()
                .post("/auth/register")
            .then()
                .spec(created)
                .extract().jsonPath().getObject("data", AuthData.class);

        userAna = ana.getUser().getUsername();
        userBogdan = bogdan.getUser().getUsername();
        tokenBogdan = bogdan.getToken();        // Bogdan folosește direct token-ul primit la register

        assertThat(userAna, startsWith("ana_"));
        assertThat(tokenBogdan, not(emptyOrNullString()));
    }

    @Test(dependsOnMethods = "etapa01_inregistrare",
          description = "POST /auth/login, GET /auth/me, POST /auth/refresh, PUT /users/profile")
    public void etapa02_autentificareSiProfil() {
        // 1. Login cu email + parolă
        tokenAna = anonim()
                .body(new LoginRequestV1(EMAIL_ANA, PAROLA))
            .when()
                .post("/auth/login")
            .then()
                .spec(ok)
                .body("data.user.username", equalTo(userAna))
                .extract().path("data.token");

        // 2. "Cine sunt eu?" - profilul + contoarele de notificări
        ca(tokenAna)
            .when()
                .get("/auth/me")
            .then()
                .spec(ok)
                .body("data.user.username", equalTo(userAna))
                .body("data.user.friends_count", equalTo(0))
                .body("data.user.counts.unread_messages", equalTo(0))
                .body("data.user.counts.pending_requests", equalTo(0));

        // 3. Refresh: un token nou, fără să retrimitem parola
        tokenAna = ca(tokenAna)
            .when()
                .post("/auth/refresh")
            .then()
                .spec(ok)
                .body("data.token", not(emptyOrNullString()))
                .extract().path("data.token");

        // 4. Update profil - body construit rapid dintr-un Map (serializat automat în JSON)
        ca(tokenAna)
                .body(Map.of("first_name", "Ana-Maria", "last_name", "Tester" + TS))
            .when()
                .put("/users/profile")
            .then()
                .spec(ok)
                .body("data.message", equalTo("Profile updated successfully"))
                .body("data.user.first_name", equalTo("Ana-Maria"))
                .body("data.user.name", startsWith("Ana-Maria"));
    }

    @Test(dependsOnMethods = "etapa02_autentificareSiProfil",
          description = "POST /upload/media, POST /users/profile/picture")
    public void etapa03_upload() {
        // Upload imagine ca Base64 în JSON (alternativa multipart e în Exercițiul 3)
        imagePath = ca(tokenAna)
                .body(Map.of("base64", PIXEL_PNG, "folder", "posts"))
            .when()
                .post("/upload/media")
            .then()
                .spec(created)
                .body("data.mime_type", equalTo("image/png"))
                .body("data.file_path", startsWith("assets/images/posts/"))
                .extract().path("data.file_path");

        // Schimbare avatar
        ca(tokenAna)
                .body(Map.of("base64", PIXEL_PNG))
            .when()
                .post("/users/profile/picture")
            .then()
                .spec(ok)
                .body("data.message", equalTo("Profile avatar updated successfully"))
                .body("data.profile_pic", startsWith("assets/images/profile_pics/"));
    }

    @Test(dependsOnMethods = "etapa03_upload",
          description = "POST /posts, GET /posts, GET/PUT /posts/{id}, POST /posts/{id}/pin")
    public void etapa04_postari() {
        CreatePostRequest cerere = new CreatePostRequest(
                "Primul meu post automatizat #" + HASHTAG,
                imagePath,                                      // imaginea urcată în Etapa 3
                new CreatePostRequest.Poll("Framework preferat?",
                        List.of("REST Assured", "Postman", "Karate")));

        PostDto post = ca(tokenAna)
                .body(cerere)
            .when()
                .post("/posts")
            .then()
                .spec(created)
                .body("data.user_context.is_author", equalTo(true))
                .body("data.poll.options", hasSize(3))
                .body("data.poll.options.option_text", hasItems("REST Assured", "Postman", "Karate"))
                .extract().jsonPath().getObject("data", PostDto.class);

        postId = post.getId();
        pollOptionId = post.getPoll().getOptions().get(0).getId();   // id-ul opțiunii "REST Assured"
        assertThat(post.getImageUrl(), equalTo(imagePath));

        // Feed-ul personalizat, paginat cu query params
        ca(tokenAna)
                .queryParam("page", 1)
                .queryParam("limit", 5)
            .when()
                .get("/posts")
            .then()
                .spec(ok)
                .body("data.id", hasItem(postId))
                .body("meta.page", equalTo(1))
                .body("meta.limit", equalTo(5));

        // Path param REAL: /posts/{id}
        ca(tokenAna)
                .pathParam("id", postId)
            .when()
                .get("/posts/{id}")
            .then()
                .spec(ok)
                .body("data.id", equalTo(postId))
                .body("data.added_by.username", equalTo(userAna));

        // Editare (doar autorul are voie)
        ca(tokenAna)
                .pathParam("id", postId)
                .body(Map.of("body", "Post editat #" + HASHTAG))
            .when()
                .put("/posts/{id}")
            .then()
                .spec(ok)
                .body("data.is_edited", equalTo(true))
                .body("data.body", startsWith("Post editat"));

        // Fixare pe profil (toggle)
        ca(tokenAna)
                .pathParam("id", postId)
            .when()
                .post("/posts/{id}/pin")
            .then()
                .spec(ok)
                .body("data.is_pinned", equalTo(true))
                .body("data.action", equalTo("pinned"));
    }

    @Test(dependsOnMethods = "etapa04_postari",
          description = "Reacții, bookmark, repost, vot în sondaj, impresii și statistici")
    public void etapa05_interactiuni() {
        // Bogdan reacționează cu "love" (endpoint de tip TOGGLE)
        ca(tokenBogdan)
                .pathParam("id", postId)
                .body(Map.of("reaction", "love"))
            .when()
                .post("/posts/{id}/react")
            .then()
                .spec(ok)
                .body("data.reacted", equalTo(true))
                .body("data.user_reaction", equalTo("love"))
                .body("data.likes_count", equalTo(1));

        // Ana vede cine a reacționat și cum
        ca(tokenAna)
                .pathParam("id", postId)
            .when()
                .get("/posts/{id}/reactions")
            .then()
                .spec(ok)
                .body("data.total_reactions", equalTo(1))
                .body("data.summary.love", equalTo(1))
                .body("data.reactors.username", hasItem(userBogdan));

        // Bogdan salvează postarea și o regăsește în lista de bookmark-uri
        ca(tokenBogdan)
                .pathParam("id", postId)
            .when()
                .post("/posts/{id}/save")
            .then()
                .spec(ok)
                .body("data.is_saved", equalTo(true));

        ca(tokenBogdan)
            .when()
                .get("/posts/saved")
            .then()
                .spec(ok)
                .body("data.id", hasItem(postId))
                // Groovy GPath: găsim postarea noastră în listă și verificăm un câmp imbricat
                .body("data.find { it.id == %s }.user_context.is_saved", withArgs(postId), equalTo(true));

        // Repost cu comentariu -> o postare NOUĂ care o conține pe cea originală
        repostId = ca(tokenBogdan)
                .pathParam("id", postId)
                .body(Map.of("comment", "Foarte util!"))
            .when()
                .post("/posts/{id}/repost")
            .then()
                .spec(created)
                .body("data.shared_post.id", equalTo(postId))
                .body("data.added_by.username", equalTo(userBogdan))
                .extract().path("data.id");

        // Vot în sondaj, folosind id-ul opțiunii extras în Etapa 4
        ca(tokenBogdan)
                .pathParam("id", postId)
                .body(Map.of("option_id", pollOptionId))
            .when()
                .post("/posts/{id}/poll/vote")
            .then()
                .spec(ok)
                .body("data.total_votes", equalTo(1))
                .body("data.user_voted_opt", equalTo(pollOptionId))
                .body("data.options.find { it.id == %s }.percent", withArgs(pollOptionId), equalTo(100));

        // Impresii (batch) - aplicațiile mobile le trimit în grup, nu câte una
        ca(tokenBogdan)
                .body(Map.of("post_ids", List.of(postId)))
            .when()
                .post("/posts/views")
            .then()
                .spec(ok)
                .body("data.recorded_impressions", equalTo(1));

        // Statistici detaliate - DOAR pentru autor
        ca(tokenAna)
                .pathParam("id", postId)
            .when()
                .get("/posts/{id}/views")
            .then()
                .spec(ok)
                .body("data.views", greaterThanOrEqualTo(1))
                .body("data.shares", equalTo(1))
                .body("data.recent_viewers.username", hasItem(userBogdan));
    }

    @Test(dependsOnMethods = "etapa05_interactiuni",
          description = "Comentariu cu @mention, listare paginată, ștergere de către autorul postării")
    public void etapa06_comentarii() {
        commentId = ca(tokenBogdan)
                .pathParam("id", postId)
                .body(Map.of("body", "Super! @" + userAna))    // @mention -> notificare pentru Ana
            .when()
                .post("/posts/{id}/comments")
            .then()
                .spec(created)
                .body("data.post_id", equalTo(postId))
                .body("data.author.username", equalTo(userBogdan))
                .extract().path("data.id");

        ca(tokenAna)
                .pathParam("id", postId)
                .queryParam("page", 1)
                .queryParam("limit", 10)
            .when()
                .get("/posts/{id}/comments")
            .then()
                .spec(ok)
                .body("data.id", hasItem(commentId))
                .body("meta.has_more", equalTo(false));

        // Autorul POSTĂRII poate șterge orice comentariu de pe postarea sa
        ca(tokenAna)
                .pathParam("id", commentId)
            .when()
                .delete("/comments/{id}")
            .then()
                .spec(ok)
                .body("data.deleted", equalTo(true));
    }

    @Test(dependsOnMethods = "etapa06_comentarii", description = "Fluxul complet de prietenie")
    public void etapa07_prietenii() {
        // Ana trimite cererea, se răzgândește (cancel), apoi o retrimite
        ca(tokenAna).pathParam("username", userBogdan)
            .when().post("/friends/request/{username}")
            .then().spec(ok).body("data.status", equalTo("sent"));

        ca(tokenAna).pathParam("username", userBogdan)
            .when().post("/friends/cancel/{username}")
            .then().spec(ok).body("data.status", equalTo("cancelled"));

        ca(tokenAna).pathParam("username", userBogdan)
            .when().post("/friends/request/{username}")
            .then().spec(ok).body("data.status", equalTo("sent"));

        // Bogdan vede cererea primită și o acceptă
        ca(tokenBogdan)
            .when().get("/friends/requests")
            .then().spec(ok)
                .body("data.total_incoming", equalTo(1))
                .body("data.incoming.username", hasItem(userAna));

        ca(tokenBogdan)
                .body(Map.of("from_user", userAna, "action", "accept"))
            .when().post("/friends/respond")
            .then().spec(ok).body("data.status", equalTo("friends"));

        // Verificări din perspectiva Anei
        ca(tokenAna).pathParam("username", userAna)
            .when().get("/users/{username}/friends")
            .then().spec(ok).body("data.username", hasItem(userBogdan));

        ca(tokenAna).pathParam("username", userBogdan)
            .when().get("/users/{username}")
            .then().spec(ok)
                .body("data.relationship.is_friend", equalTo(true))
                .body("data.friends_count", equalTo(1))
                .body("data.is_closed", equalTo(false));

        ca(tokenAna).pathParam("username", userBogdan)
            .when().get("/users/{username}/mutual-friends")
            .then().spec(ok)
                .body("data.target_username", equalTo(userBogdan))
                .body("data.mutual_count", equalTo(0));

        // Timeline-ul Anei: postarea fixată în Etapa 4 are is_pinned = true
        ca(tokenAna).pathParam("username", userAna)
                .queryParam("page", 1).queryParam("limit", 10)
            .when().get("/users/{username}/posts")
            .then().spec(ok)
                .body("data.find { it.id == %s }.is_pinned", withArgs(postId), equalTo(true));
    }

    @Test(dependsOnMethods = "etapa07_prietenii", description = "Mesagerie directă între Ana și Bogdan")
    public void etapa08_mesaje() {
        messageId = ca(tokenAna)
                .pathParam("username", userBogdan)
                .body(Map.of("body", "Salut! Testam API-ul?"))
            .when().post("/messages/{username}")
            .then().spec(created)
                .body("data.is_mine", equalTo(true))
                .body("data.opened", equalTo(false))
                .extract().path("data.id");

        // Indicatorul "Ana scrie..."
        ca(tokenAna)
                .body(Map.of("user_to", userBogdan, "is_typing", true))
            .when().post("/messages/typing")
            .then().spec(ok);

        // Bogdan: lista de conversații, badge-ul de necitite și indicatorul de typing
        ca(tokenBogdan)
            .when().get("/messages/conversations")
            .then().spec(ok)
                .body("data.find { it.partner.username == '%s' }.unread_count", withArgs(userAna), equalTo(1))
                .body("data.find { it.partner.username == '%s' }.is_typing", withArgs(userAna), equalTo(true));

        ca(tokenBogdan).pathParam("username", userAna)
                .queryParam("page", 1).queryParam("limit", 20)
            .when().get("/messages/{username}")
            .then().spec(ok)
                .body("data.body", hasItem("Salut! Testam API-ul?"));

        ca(tokenBogdan).pathParam("username", userAna)
            .when().post("/messages/{username}/seen")
            .then().spec(ok).body("data.status", equalTo("seen"));

        // Doar EXPEDITORUL își poate retrage mesajul
        ca(tokenAna).pathParam("id", messageId)
            .when().delete("/messages/{id}")
            .then().spec(ok).body("data.deleted", equalTo(true));
    }

    @Test(dependsOnMethods = "etapa08_mesaje",
          description = "Notificări, căutare, trending, hashtag-uri, link preview")
    public void etapa09_descoperire() {
        // Comentariul cu @mention din Etapa 6 a generat o notificare pentru Ana
        ca(tokenAna)
            .when().get("/notifications")
            .then().spec(ok)
                .body("data.unread_count", greaterThanOrEqualTo(1))
                .body("data.notifications.message", hasItem(containsString("commented on your post")));

        ca(tokenAna).when().post("/notifications/mark-read").then().spec(ok);

        ca(tokenAna)
            .when().get("/notifications")
            .then().spec(ok).body("data.unread_count", equalTo(0));

        // Căutare utilizatori (după nume) și postări (full-text)
        ca(tokenAna).queryParam("q", "Tester" + TS)
            .when().get("/search")
            .then().spec(ok).body("data.username", hasItems(userAna, userBogdan));

        ca(tokenAna).queryParam("q", HASHTAG)
            .when().get("/search/posts")
            .then().spec(ok).body("data.id", hasItem(postId));

        // Trending: lista nu e goală, iar titlurile au forma "#tag"
        ca(tokenAna)
            .when().get("/trends")
            .then().spec(ok)
                .body("data", not(empty()))
                .body("data[0].title", startsWith("#"))
                .body("data[0].hits", greaterThan(0));

        // Feed-ul unui hashtag - path param fără "#"
        ca(tokenAna).pathParam("tag", HASHTAG)
            .when().get("/hashtags/{tag}")
            .then().spec(ok).body("data.id", hasItem(postId));

        // Endpoint PUBLIC: metadate OpenGraph pentru orice URL
        anonim().queryParam("url", "https://www.example.com")
            .when().get("/links/preview")
            .then().spec(ok)
                .body("data.domain", equalTo("example.com"))
                .body("data.title", equalTo("Example Domain"));
    }

    @Test(dependsOnMethods = "etapa09_descoperire", description = "Token de push: register / unregister")
    public void etapa10_dispozitive() {
        ca(tokenAna)
                .body(Map.of("token", DEVICE_TOKEN, "platform", "android"))   // ios | android | web
            .when().post("/devices/register")
            .then().spec(ok)
                .body("data.status", equalTo("registered"))
                .body("data.platform", equalTo("android"));

        // DELETE cu query param
        ca(tokenAna).queryParam("token", DEVICE_TOKEN)
            .when().delete("/devices/unregister")
            .then().spec(ok).body("data.status", equalTo("unregistered"));
    }

    @Test(dependsOnMethods = "etapa10_dispozitive", description = "Unfriend, ștergeri, teste de autorizare")
    public void etapa11_curatenieSiAutorizare() {
        ca(tokenAna).pathParam("username", userBogdan)
            .when().post("/friends/remove/{username}")
            .then().spec(ok).body("data.is_friend", equalTo(false));

        ca(tokenBogdan).pathParam("id", repostId)
            .when().delete("/posts/{id}")
            .then().spec(ok).body("data.deleted", equalTo(true));

        // AUTORIZARE: Bogdan este autentificat, dar NU are voie să șteargă postarea Anei
        ca(tokenBogdan).pathParam("id", postId)
            .when().delete("/posts/{id}")
            .then().spec(eroare(403))
                .body("message", containsString("only delete your own posts"));

        ca(tokenAna).pathParam("id", postId)
            .when().delete("/posts/{id}")
            .then().spec(ok).body("data.post_id", equalTo(postId));

        // După ștergere, resursa nu mai există
        ca(tokenAna).pathParam("id", postId)
            .when().get("/posts/{id}")
            .then().spec(eroare(404))
                .body("message", equalTo("Post not found or deleted"));
    }

    @Test(dependsOnMethods = "etapa11_curatenieSiAutorizare",
          description = "Forgot/reset password, logout, close-account")
    public void etapa12_securitateSiInchidereConturi() {
        // Răspuns generic: nu dezvăluie dacă email-ul există sau nu
        anonim().body(Map.of("email", EMAIL_ANA))
            .when().post("/auth/forgot-password")
            .then().spec(ok).body("data.reset_token_sent", equalTo(true));

        // Token-ul real ajunge pe email, deci aici testăm cazul NEGATIV
        anonim().body(Map.of("token", "token_invalid_123", "new_password", "Nou!Parola2026"))
            .when().post("/auth/reset-password")
            .then().spec(eroare(400))
                .body("message", equalTo("Invalid or expired password reset token"));

        // Logout: token-ul este REVOCAT pe server (opțional ștergem și token-ul de push)
        ca(tokenAna).body(Map.of("device_token", DEVICE_TOKEN))
            .when().post("/auth/logout")
            .then().spec(ok).body("data.status", equalTo("logged_out"));

        ca(tokenAna)
            .when().get("/auth/me")
            .then().spec(eroare(401))
                .body("message", equalTo("Unauthorized: Invalid or expired token"));

        // Login din nou - de data aceasta cu USERNAME-ul - ca să putem închide contul
        String tokenNou = anonim().body(new LoginRequestV1(userAna, PAROLA))
            .when().post("/auth/login")
            .then().spec(ok).extract().path("data.token");

        ca(tokenNou).when().post("/users/close-account")
            .then().spec(ok).body("data.status", equalTo("closed"));

        ca(tokenBogdan).when().post("/users/close-account")
            .then().spec(ok).body("data.status", equalTo("closed"));

        // Un cont închis nu se mai poate autentifica
        anonim().body(new LoginRequestV1(EMAIL_ANA, PAROLA))
            .when().post("/auth/login")
            .then().spec(eroare(401))
                .body("message", equalTo("Invalid credentials or account closed"));
    }
}
