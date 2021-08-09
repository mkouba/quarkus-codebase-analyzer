package com.github.mkouba;

import java.util.Arrays;
import java.util.Objects;

class QuarkusVersion implements Comparable<QuarkusVersion> {

    private final int major;
    private final int minor;
    private final int bugfix;
    private final String classifier;

    public QuarkusVersion(String value) {
        String[] parts = value.split("\\.");
        if (parts.length < 3) {
            throw new IllegalArgumentException("Invalid Quarkus version: " + value + "; parts: " + Arrays.toString(parts));
        }
        this.major = Integer.parseInt(parts[0]);
        this.minor = Integer.parseInt(parts[1]);
        this.bugfix = Integer.parseInt(parts[2]);
        this.classifier = parts.length < 4 ? "" : parts[3];
    }

    @Override
    public int compareTo(QuarkusVersion o) {
        int ret = Integer.compare(major, o.major);
        if (ret == 0) {
            ret = Integer.compare(minor, o.minor);
        }
        if (ret == 0) {
            ret = Integer.compare(bugfix, o.bugfix);
        }
        if (ret == 0) {
            ret = classifier.compareTo(o.classifier);
        }
        return ret;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bugfix, classifier, major, minor);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        QuarkusVersion other = (QuarkusVersion) obj;
        return bugfix == other.bugfix && Objects.equals(classifier, other.classifier) && major == other.major
                && minor == other.minor;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder().append(major).append(".").append(minor).append(".").append(bugfix);
        if (!classifier.isEmpty()) {
            builder.append(".").append(classifier);
        }
        return builder.toString();

    }

}