package net.aerh.jigsaw.core.resource;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PackMetadataTest {

    @Test
    void fromJson_stringDescription() {
        PackMetadata meta = PackMetadata.fromJson("""
                {
                  "pack": {
                    "pack_format": 34,
                    "description": "My resource pack"
                  }
                }
                """);

        assertThat(meta.packFormat()).isEqualTo(34);
        assertThat(meta.description()).isEqualTo("My resource pack");
    }

    @Test
    void fromJson_missingDescription() {
        PackMetadata meta = PackMetadata.fromJson("""
                {
                  "pack": {
                    "pack_format": 34
                  }
                }
                """);

        assertThat(meta.packFormat()).isEqualTo(34);
        assertThat(meta.description()).isEmpty();
    }

    @Test
    void fromJson_objectDescription_extractsText() {
        PackMetadata meta = PackMetadata.fromJson("""
                {
                  "pack": {
                    "pack_format": 46,
                    "description": {
                      "text": "My Pack",
                      "color": "gold",
                      "bold": true
                    }
                  }
                }
                """);

        assertThat(meta.packFormat()).isEqualTo(46);
        assertThat(meta.description()).isEqualTo("My Pack");
    }

    @Test
    void fromJson_objectDescription_withExtra() {
        PackMetadata meta = PackMetadata.fromJson("""
                {
                  "pack": {
                    "pack_format": 46,
                    "description": {
                      "text": "Main ",
                      "extra": [
                        {"text": "part", "color": "red"},
                        " end"
                      ]
                    }
                  }
                }
                """);

        assertThat(meta.description()).isEqualTo("Main part end");
    }

    @Test
    void fromJson_arrayDescription() {
        PackMetadata meta = PackMetadata.fromJson("""
                {
                  "pack": {
                    "pack_format": 46,
                    "description": [
                      {"text": "Line 1", "color": "red"},
                      "\\n",
                      {"text": "Line 2"}
                    ]
                  }
                }
                """);

        assertThat(meta.packFormat()).isEqualTo(46);
        assertThat(meta.description()).isEqualTo("Line 1\nLine 2");
    }

    @Test
    void fromJson_translateDescription() {
        PackMetadata meta = PackMetadata.fromJson("""
                {
                  "pack": {
                    "pack_format": 46,
                    "description": {
                      "translate": "pack.description.key"
                    }
                  }
                }
                """);

        assertThat(meta.description()).isEqualTo("pack.description.key");
    }

    @Test
    void fromJson_missingPackObject_throws() {
        assertThatThrownBy(() -> PackMetadata.fromJson("""
                { "not_pack": {} }
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing 'pack' object");
    }
}
