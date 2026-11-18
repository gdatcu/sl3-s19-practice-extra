package ro.qatester.api.models;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Din Capitolul 3 - se potrivește exact cu POST /auth/register din API v1 */
public class RegisterRequest {
    @JsonProperty("first_name")
    private String firstName;

    @JsonProperty("last_name")
    private String lastName;

    private String email;
    private String password;

    public RegisterRequest(String firstName, String lastName, String email, String password) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.password = password;
    }

    // Getteri și Setteri sunt necesari pentru Jackson
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getPassword() { return password; }
}
