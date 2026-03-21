package dev.anvilcraft.resource.cuminum.test;

import java.util.ArrayList;
import java.util.List;

public class UITest {
    public record Style(int height, int width) {
    }

    public static class Layout {
        List<Button> buttons = new ArrayList<>();

        public Layout(Style style) {
        }

        public Layout() {
            this(new Style(0, 0));
        }

        public class Button {
            public static class Style {
                private Style() {
                }

                public static Style create() {
                    return new Style();
                }

                Style height(int height) {
                    return this;
                }

                Style width(int width) {
                    return this;
                }
            }

            public Button(Style style) {
                Layout.this.buttons.add(this);
            }

            public Button() {
                this(Style.create());
            }

            public void setText(String text) {
            }
        }
    }

    public static void main(String[] args) {
        new Layout(new Style(100, 100)) {{
            new Button(
                Button.Style
                    .create()
                    .height(1000)
                    .width(1000)
            ) {{
                setText("Click me");
            }};
        }};
    }
}
