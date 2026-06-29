package pl.nbp.copilot.policy;

import org.junit.jupiter.api.Test;
import pl.nbp.copilot.model.CaseType;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyProviderTest {

    private final PolicyProvider provider;

    PolicyProviderTest() throws Exception {
        provider = new PolicyProvider();
    }

    @Test
    void zwrotPolicyContains14Dni() {
        assertThat(provider.getPolicy(CaseType.ZWROT)).contains("14 dni");
    }

    @Test
    void reklamacjaPolicyContainsRekojmi() {
        assertThat(provider.getPolicy(CaseType.REKLAMACJA)).contains("rękojmi");
    }

    @Test
    void policiesAreDifferent() {
        assertThat(provider.getPolicy(CaseType.ZWROT))
            .isNotEqualTo(provider.getPolicy(CaseType.REKLAMACJA));
    }
}
