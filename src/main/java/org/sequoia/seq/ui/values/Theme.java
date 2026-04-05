package org.sequoia.seq.ui.values;

import java.awt.Color;


// This isn't a record because records don't allow for public fields. Sometimes idiomatic java is stupid.
public class Theme {

	public final String NAME;
	public final Background background;
	public final Accent accent;
	public final Text text;
	public final Element element;
	
	public class Background {
		// SEMI TRANSPARENT COLORS
		public final Color OVERLAY; // Used for full screen overlays. Examples are: main menu background and the overlay applied when a modal is being shown.
		public final Color SIDEBAR; // Used for the sidebar listing the different menus.
		public final Color BODY; // Used for main sections containing elements. Should be semi-transparent.
		public final Color HEADER; // Used for
		public final Color CONTENT;
		public final Color CONTENT_FOCUSED;
		// OPAQUE COLORS
		public final Color BODY_OPAQUE; //

		Background(
			Color overlay,
			Color sidebar,
			Color body,
			Color body_opaque,
			Color header,
			Color content,
			Color content_focused
		) {
			OVERLAY = overlay;
			SIDEBAR = sidebar;
			BODY = body;
			BODY_OPAQUE = body_opaque;
			HEADER = header;
			CONTENT = content;
			CONTENT_FOCUSED = content_focused;
		}
	}

	public class Accent {
		public final Color MAIN_LIGHT;
		public final Color MAIN_LIGHT_HOVER;
		public final Color MAIN_DARK;
		public final Color MAIN_DARK_HOVER;
		public final Color MAIN_INACTIVE;
		public final Color ALT_LIGHT;
		public final Color ALT_DARK;

		Accent(
			Color main_light,
			Color main_light_hover,
			Color main_dark,
			Color main_dark_hover,
			Color main_inactive,
			Color alt_light,
			Color alt_dark
		) {
			MAIN_LIGHT = main_light;
			MAIN_LIGHT_HOVER = main_light_hover;
			MAIN_DARK = main_dark;
			MAIN_DARK_HOVER = main_dark_hover;
			MAIN_INACTIVE = main_inactive;
			ALT_LIGHT = alt_light;
			ALT_DARK = alt_dark;
		}
	}

	public class Text {
		public final Color PRIMARY;
		public final Color PLEASANT;
		public final Color FAINT;
		public final Color INACTIVE;

		Text(
			Color primary,
			Color pleasant,
			Color faint,
			Color inactive
		) {
			PRIMARY = primary;
			PLEASANT = pleasant;
			FAINT = faint;
			INACTIVE = inactive;
		}
	}

	public class Element {
		public final Color INPUT_PRIMARY;
		public final Color INPUT_SECONDARY;
		public final Color INPUT_HOVER;
		public final Color SCROLLBAR_TRACK;
		public final Color SCROLLBAR_THUMB;
		public final Color DANGER_PRIMARY;
		public final Color DANGER_HOVER;
		public final Color WARNING_PRIMARY;
		public final Color GOOD_PRIMARY;

		Element (
			Color input_primary,
			Color input_secondary,
			Color input_hover,
			Color scrollbar_track,
			Color scrollbar_thumb,
			Color danger_primary,
			Color danger_hover,
			Color warning_primary,
			Color good_primary
		) {
			INPUT_PRIMARY = input_primary;
			INPUT_SECONDARY = input_secondary;
			INPUT_HOVER = input_hover;
			SCROLLBAR_TRACK = scrollbar_track;
			SCROLLBAR_THUMB = scrollbar_thumb;
			DANGER_PRIMARY = danger_primary;
			DANGER_HOVER = danger_hover;
			WARNING_PRIMARY = warning_primary;
			GOOD_PRIMARY = good_primary;
		}
	}

	public Theme(
		String name,

		Color background_overlay,
		Color background_sidebar,
		Color background_body,
		Color background_body_opaque,
		Color background_header,
		Color background_content,
		Color background_content_focused,

		Color accent_main_light,
		Color accent_main_light_hover,
		Color accent_main_dark,
		Color accent_main_dark_hover,
		Color accent_main_inactive,
		Color accent_alt_light,
		Color accent_alt_dark,

		Color text_primary,
		Color text_pleasant,
		Color text_faint,
		Color text_inactive,

		Color element_input_primary,
		Color element_input_secondary,
		Color element_input_hover,
		Color element_scrollbar_track,
		Color element_scrollbar_thumb,
		Color element_danger_primary,
		Color element_danger_hover,
		Color element_warning_primary,
		Color element_good_primary
	) {
		NAME = name;

		background = new Background(
			background_overlay,
			background_sidebar,
			background_body,
			background_body_opaque,
			background_header,
			background_content,
			background_content_focused
		);

		accent = new Accent(
			accent_main_light,
			accent_main_light_hover,
			accent_main_dark,
			accent_main_dark_hover,
			accent_main_inactive,
			accent_alt_light,
			accent_alt_dark
		);

		text = new Text(
			text_primary,
			text_pleasant,
			text_faint,
			text_inactive
		);

		element = new Element(
			element_input_primary,
			element_input_secondary,
			element_input_hover,
			element_scrollbar_track,
			element_scrollbar_thumb,
			element_danger_primary,
			element_danger_hover,
			element_warning_primary,
			element_good_primary
		);
	}
}
