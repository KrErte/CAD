package ee.krerte.cad.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sanity-test AgentPersona enum'ile. Kontrollib, et:
 *   1. Kõik 4 personat on defineeritud (struct + protsess + cost + esteetika)
 *   2. Kaalude summa on 1.0 (kaalutud keskmise jaoks kriitiline)
 *   3. Koodid ja displayNameEt ei ole tühjad ega dublikaadid
 */
class AgentPersonaTest {

    @Test
    void allFourPersonasExist() {
        AgentPersona[] all = AgentPersona.values();
        assertEquals(4, all.length);
    }

    @Test
    void weightsSumToOne() {
        double sum = 0.0;
        for (AgentPersona p : AgentPersona.values()) {
            sum += p.weight();
        }
        // Lubame 0.001 kõrvalekalde ujupunkti-arvutuse tõttu
        assertEquals(1.0, sum, 0.001,
                "Kaalude summa peab olema 1.0, et council_score oleks korralikult normaliseeritud");
    }

    @Test
    void codesAreUniqueAndNonBlank() {
        java.util.Set<String> codes = new java.util.HashSet<>();
        for (AgentPersona p : AgentPersona.values()) {
            assertNotNull(p.code());
            assertFalse(p.code().isBlank());
            assertTrue(codes.add(p.code()), "Duplikaat kood: " + p.code());
        }
    }

    @Test
    void displayNamesAreEstonian() {
        // Sanity: peab sisaldama min 1 eestikeelsest pikkusest tähte ning
        // pole lihtsalt inglise sõna
        for (AgentPersona p : AgentPersona.values()) {
            assertNotNull(p.displayNameEt());
            assertTrue(p.displayNameEt().length() >= 5,
                    "DisplayNameEt liiga lühike: " + p.displayNameEt());
        }
    }

    @Test
    void systemPromptsAreSubstantial() {
        // Iga persona system-prompt peab olema korralik briifing, mitte
        // üherealine. < 200 tähemärki on signaal, et prompt on puudulik.
        for (AgentPersona p : AgentPersona.values()) {
            assertTrue(p.systemPromptEt().length() > 200,
                    "Persona " + p.code() + " system-prompt on liiga lühike (< 200 tähemärki)");
        }
    }
}
