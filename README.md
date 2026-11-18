# hapifyMe REST API v1 – Flux E2E (Sesiunea 19, Capitolul 6)

Proiectul Maven cu tot codul din **Capitolul 6** al materialului „Curs 19 – Material auxiliar”.
Testele rulează pe API-ul live: https://apps.qualiadept.eu/hapifyme/api/v1
(documentație: https://apps.qualiadept.eu/hapifyme/api/v1/docs/index.html)

## Cerințe
- JDK 17+
- Maven 3.9+ (sau IntelliJ IDEA, care are Maven inclus)

## Structura
```
src/test/java/ro/qatester/api/
├── models/RegisterRequest.java          // din Capitolul 3
└── v1/
    ├── BaseApiTest.java                 // 6.3 - specs + JWT
    ├── HapifyMeE2EFlowTest.java         // 6.6 - fluxul E2E, 13 etape, 53/53 endpoint-uri
    ├── HapifyMeNegativeTest.java        // 6.7 - teste negative (DataProvider)
    ├── HapifyMeExercitiiTest.java       // 6.12 - soluțiile exercițiilor 1-4
    └── models/                          // 6.4 - POJO-uri v1
src/test/resources/
├── testng-e2e.xml                       // 6.8 - E2E + negative
├── testng-exercitii.xml                 // soluțiile exercițiilor
└── pixel.png                            // imagine 1x1 pentru Exercițiul 3 (multipart)
```

## Rulare
```
# Fluxul E2E + testele negative (ca în 6.8)
mvn test -Dsurefire.suiteXmlFiles=src/test/resources/testng-e2e.xml

# Soluțiile exercițiilor
mvn test -Dsurefire.suiteXmlFiles=src/test/resources/testng-exercitii.xml

# Toate clasele de test
mvn test
```
Din IntelliJ: click dreapta pe `testng-e2e.xml` → **Run**.

## De știut
- Fiecare rulare creează utilizatori noi (email cu timestamp) și îi închide la final.
- Etapele E2E depind una de alta (`dependsOnMethods`): dacă o etapă pică, următoarele apar **SKIPPED**.
- Exercițiul 2 (paginare) nu verifică `has_more` pe ultima pagină: la scrierea materialului API-ul returna
  `true` și acolo (posibil defect, vezi 6.12).
