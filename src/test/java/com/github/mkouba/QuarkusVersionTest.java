package com.github.mkouba;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

public class QuarkusVersionTest {

    @Test
    public void testVersions() {
        QuarkusVersion v100 = new QuarkusVersion("1.0.0.CR1");
        QuarkusVersion v1110 = new QuarkusVersion("1.11.0.Final");
        QuarkusVersion v1103 = new QuarkusVersion("1.10.3.Final");
        QuarkusVersion v091 = new QuarkusVersion("0.9.1");
        List<QuarkusVersion> versions = new ArrayList<>();
        versions.add(v100);
        versions.add(v1110);
        versions.add(v1103);
        versions.add(v091);
        Collections.sort(versions);
        assertEquals(v091, versions.get(0));
        assertEquals(v100, versions.get(1));
        assertEquals(v1103, versions.get(2));
        assertEquals(v1110, versions.get(3));
    }

}
