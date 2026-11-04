package com.codex.guardian.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.border.AbstractBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.AbstractDocument;

/**
 * The few inputs the watch rule editor needs, drawn in the Codex look rather than the platform's: a text field set
 * into the ink, a picker that opens a short list, a number with a step either side, a lit toggle chip, colour
 * swatches and an on/off switch.
 */
public final class Controls
{
	private static final int FIELD_H = 22;

	private Controls()
	{
	}

	/** A one-line field that holds at most {@code max} characters and calls back on every change. */
	public static JTextField field(String text, int max, Consumer<String> onChange)
	{
		JTextField f = new JTextField();
		((AbstractDocument) f.getDocument()).setDocumentFilter(new Limit(max, false));
		f.setText(text == null ? "" : text);
		style(f);
		f.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				onChange.accept(f.getText());
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				onChange.accept(f.getText());
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
			}
		});
		return f;
	}

	static void style(JTextField f)
	{
		f.setFont(Theme.body());
		f.setForeground(Theme.TEXT);
		f.setCaretColor(Theme.GOLD_BRIGHT);
		f.setSelectionColor(Theme.alpha(Theme.GOLD, 90));
		f.setSelectedTextColor(Theme.TEXT);
		f.setBackground(Theme.INK_DEEP);
		f.setOpaque(true);
		f.setBorder(new Inset());
		f.setPreferredSize(new Dimension(10, FIELD_H));
		f.setMinimumSize(new Dimension(10, FIELD_H));
		f.setMaximumSize(new Dimension(Integer.MAX_VALUE, FIELD_H));
		f.addFocusListener(new FocusAdapter()
		{
			@Override
			public void focusGained(FocusEvent e)
			{
				f.repaint();
			}

			@Override
			public void focusLost(FocusEvent e)
			{
				f.repaint();
			}
		});
	}

	/** A sunken edge, lit gold while the field has focus. */
	private static final class Inset extends AbstractBorder
	{
		@Override
		public void paintBorder(java.awt.Component c, Graphics graphics, int x, int y, int w, int h)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
			g.setColor(c.hasFocus() ? Theme.alpha(Theme.GOLD, 170) : Theme.STROKE);
			g.draw(new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, w - 1, h - 1, 6, 6));
			g.setColor(Theme.alpha(java.awt.Color.BLACK, 70));
			g.drawLine(x + 3, y + 1, x + w - 4, y + 1);
			g.dispose();
		}

		@Override
		public Insets getBorderInsets(java.awt.Component c)
		{
			return new Insets(2, 6, 2, 6);
		}
	}

	/** Keeps a field's text under a length, and to digits only when asked. */
	static final class Limit extends DocumentFilter
	{
		private final int max;
		private final boolean digits;

		Limit(int max, boolean digits)
		{
			this.max = max;
			this.digits = digits;
		}

		private String allowed(String s)
		{
			if (s == null)
			{
				return "";
			}
			StringBuilder out = new StringBuilder();
			for (char c : s.toCharArray())
			{
				if (digits ? Character.isDigit(c) : !Character.isISOControl(c))
				{
					out.append(c);
				}
			}
			return out.toString();
		}

		@Override
		public void insertString(FilterBypass fb, int offset, String text, AttributeSet attr) throws BadLocationException
		{
			replace(fb, offset, 0, text, attr);
		}

		@Override
		public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException
		{
			String add = allowed(text);
			int room = max - (fb.getDocument().getLength() - length);
			if (room <= 0 && add.length() > 0)
			{
				add = "";
			}
			else if (add.length() > room)
			{
				add = add.substring(0, room);
			}
			super.replace(fb, offset, length, add, attrs);
		}
	}

	/** A field-shaped choice with a chevron; a click opens the options under it. */
	public static final class Picker<T> extends JComponent
	{
		private final List<T> options;
		private final java.util.function.Function<T, String> label;
		private T value;
		private boolean hover;

		public Picker(List<T> options, T value, java.util.function.Function<T, String> label, Consumer<T> onPick)
		{
			this.options = options;
			this.value = value;
			this.label = label;
			setFont(Theme.body());
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			setPreferredSize(new Dimension(10, FIELD_H));
			setMinimumSize(new Dimension(10, FIELD_H));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, FIELD_H));
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseEntered(MouseEvent e)
				{
					hover = true;
					repaint();
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					hover = false;
					repaint();
				}

				@Override
				public void mousePressed(MouseEvent e)
				{
					JPopupMenu menu = new JPopupMenu();
					menu.setBackground(Theme.CARD);
					menu.setBorder(new LineBorder(Theme.alpha(Theme.GOLD, 140)));
					for (T option : options)
					{
						JMenuItem item = new JMenuItem(label.apply(option));
						item.setFont(Theme.body());
						item.setBackground(option.equals(Picker.this.value) ? Theme.CARD_HOVER : Theme.CARD);
						item.setForeground(option.equals(Picker.this.value) ? Theme.GOLD_BRIGHT : Theme.TEXT);
						item.setBorder(new EmptyBorder(3, 6, 3, 6));
						item.setPreferredSize(new Dimension(Math.max(getWidth() - 2, item.getPreferredSize().width), 20));
						item.addActionListener(a ->
						{
							Picker.this.value = option;
							repaint();
							onPick.accept(option);
						});
						menu.add(item);
					}
					menu.show(Picker.this, 0, getHeight());
				}
			});
		}

		public T getValue()
		{
			return value;
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
			int w = getWidth();
			int h = getHeight();
			RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, 6, 6);
			g.setPaint(new GradientPaint(0, 0, hover ? Theme.CARD_HOVER : Theme.CARD_TOP, 0, h, Theme.INK_DEEP));
			g.fill(shape);
			g.setColor(hover ? Theme.alpha(Theme.GOLD, 170) : Theme.alpha(Theme.GOLD, 90));
			g.draw(shape);
			g.setFont(getFont());
			FontMetrics fm = g.getFontMetrics();
			String text = clip(value == null ? "" : label.apply(value), fm, w - 26);
			ProgressRing.drawShadowed(g, text, 7, (h + fm.getAscent()) / 2 - 2, Theme.TEXT);
			// A small engraved chevron.
			int cx = w - 11;
			int cy = h / 2;
			Path2D chevron = new Path2D.Float();
			chevron.moveTo(cx - 4, cy - 2);
			chevron.lineTo(cx, cy + 2);
			chevron.lineTo(cx + 4, cy - 2);
			g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.setColor(hover ? Theme.GOLD_BRIGHT : Theme.GOLD);
			g.draw(chevron);
			g.dispose();
		}
	}

	/** A whole number between bounds, typed or stepped with the buttons either side or the mouse wheel. */
	public static final class Stepper extends JPanel
	{
		private final JTextField field = new JTextField();
		private final int min;
		private final int max;
		private final int step;
		private final IntConsumer onChange;
		private int value;

		public Stepper(int value, int min, int max, int step, String unit, IntConsumer onChange)
		{
			super(new BorderLayout(3, 0));
			this.min = min;
			this.max = max;
			this.step = step;
			this.onChange = onChange;
			this.value = Math.max(min, Math.min(max, value));
			setOpaque(false);
			((AbstractDocument) field.getDocument()).setDocumentFilter(new Limit(String.valueOf(max).length(), true));
			field.setText(String.valueOf(this.value));
			style(field);
			field.setHorizontalAlignment(JTextField.CENTER);
			field.getDocument().addDocumentListener(new DocumentListener()
			{
				@Override
				public void insertUpdate(DocumentEvent e)
				{
					typed();
				}

				@Override
				public void removeUpdate(DocumentEvent e)
				{
					typed();
				}

				@Override
				public void changedUpdate(DocumentEvent e)
				{
				}
			});
			field.addFocusListener(new FocusAdapter()
			{
				@Override
				public void focusLost(FocusEvent e)
				{
					// A number typed out of range is brought back into it once you leave the field.
					set(Stepper.this.value);
				}
			});
			field.addMouseWheelListener(e -> set(Stepper.this.value - e.getWheelRotation() * step));
			JPanel row = new JPanel(new BorderLayout(3, 0));
			row.setOpaque(false);
			row.add(new Nudge(false, () -> set(Stepper.this.value - step)), BorderLayout.WEST);
			row.add(field, BorderLayout.CENTER);
			row.add(new Nudge(true, () -> set(Stepper.this.value + step)), BorderLayout.EAST);
			row.setPreferredSize(new Dimension(92, FIELD_H));
			add(row, BorderLayout.WEST);
			if (unit != null)
			{
				add(Ui.label(unit.toLowerCase(), Theme.small(), Theme.TEXT_MUTED), BorderLayout.CENTER);
			}
			setMaximumSize(new Dimension(Integer.MAX_VALUE, FIELD_H));
		}

		private void typed()
		{
			try
			{
				String t = field.getText();
				int typed = t.isEmpty() ? min : Integer.parseInt(t);
				int clamped = Math.max(min, Math.min(max, typed));
				if (clamped != value)
				{
					value = clamped;
					onChange.accept(value);
				}
			}
			catch (NumberFormatException e)
			{
				// Too long for an int: the filter keeps the length to the maximum's, so this is only an overflow.
			}
		}

		private void set(int v)
		{
			int clamped = Math.max(min, Math.min(max, v));
			boolean changed = clamped != value;
			value = clamped;
			if (!field.getText().equals(String.valueOf(clamped)))
			{
				field.setText(String.valueOf(clamped));
			}
			if (changed)
			{
				onChange.accept(value);
			}
		}

		public int getValue()
		{
			return value;
		}
	}

	/** The small minus or plus either side of a stepper. */
	private static final class Nudge extends JComponent
	{
		private final boolean plus;
		private boolean hover;

		Nudge(boolean plus, Runnable action)
		{
			this.plus = plus;
			setPreferredSize(new Dimension(20, FIELD_H));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseEntered(MouseEvent e)
				{
					hover = true;
					repaint();
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					hover = false;
					repaint();
				}

				@Override
				public void mousePressed(MouseEvent e)
				{
					action.run();
				}
			});
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
			int w = getWidth();
			int h = getHeight();
			g.setColor(Theme.alpha(Theme.GOLD, hover ? 44 : 16));
			g.fill(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, 6, 6));
			g.setColor(Theme.alpha(Theme.GOLD, hover ? 170 : 100));
			g.draw(new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, 6, 6));
			g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.setColor(hover ? Theme.GOLD_BRIGHT : Theme.GOLD);
			int cx = w / 2;
			int cy = h / 2;
			g.drawLine(cx - 4, cy, cx + 4, cy);
			if (plus)
			{
				g.drawLine(cx, cy - 4, cx, cy + 4);
			}
			g.dispose();
		}
	}

	/** A toggle that lights up gold while it is on. */
	public static final class Chip extends JComponent
	{
		private final String text;
		private boolean on;
		private boolean hover;

		public Chip(String text, boolean on, Consumer<Boolean> onToggle)
		{
			this.text = text;
			this.on = on;
			setFont(Theme.small());
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			FontMetrics fm = getFontMetrics(getFont());
			Dimension d = new Dimension(fm.stringWidth(text) + 24, 20);
			setPreferredSize(d);
			setMinimumSize(d);
			setMaximumSize(d);
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseEntered(MouseEvent e)
				{
					hover = true;
					repaint();
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					hover = false;
					repaint();
				}

				@Override
				public void mousePressed(MouseEvent e)
				{
					Chip.this.on = !Chip.this.on;
					repaint();
					onToggle.accept(Chip.this.on);
				}
			});
		}

		public boolean isOn()
		{
			return on;
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
			int w = getWidth();
			int h = getHeight();
			RoundRectangle2D shape = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, h, h);
			if (on)
			{
				g.setPaint(new GradientPaint(0, 0, Theme.alpha(Theme.GOLD, hover ? 90 : 70), 0, h, Theme.alpha(Theme.GOLD_DIM, 60)));
			}
			else
			{
				g.setColor(Theme.alpha(Theme.GOLD, hover ? 22 : 0));
			}
			g.fill(shape);
			g.setColor(on ? Theme.alpha(Theme.GOLD_BRIGHT, 200) : Theme.alpha(Theme.GOLD, hover ? 130 : 70));
			g.draw(shape);
			// A lamp: lit when on, an empty ring when off.
			Ellipse2D lamp = new Ellipse2D.Float(7, h / 2f - 3, 6, 6);
			if (on)
			{
				g.setColor(Theme.GOLD_BRIGHT);
				g.fill(lamp);
			}
			else
			{
				g.setColor(Theme.TEXT_FAINT);
				g.draw(lamp);
			}
			g.setFont(getFont());
			FontMetrics fm = g.getFontMetrics();
			ProgressRing.drawShadowed(g, text, 17, (h + fm.getAscent()) / 2 - 1, on ? Theme.GOLD_BRIGHT : Theme.TEXT_MUTED);
			g.dispose();
		}
	}

	/** A round colour swatch, ringed in gold while chosen. */
	public static final class Swatch extends JComponent
	{
		private final Color color;
		private final boolean chosen;

		public Swatch(Color color, boolean chosen, String name, Runnable onPick)
		{
			this.color = color;
			this.chosen = chosen;
			setToolTipText(name);
			Dimension d = new Dimension(20, 20);
			setPreferredSize(d);
			setMinimumSize(d);
			setMaximumSize(d);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					onPick.run();
				}
			});
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
			int s = Math.min(getWidth(), getHeight());
			if (chosen)
			{
				g.setColor(Theme.GOLD_BRIGHT);
				g.setStroke(new BasicStroke(1.5f));
				g.draw(new Ellipse2D.Float(1, 1, s - 2.5f, s - 2.5f));
			}
			g.setPaint(new GradientPaint(0, 4, Theme.mix(color, Color.WHITE, 0.2), 0, s - 4, Theme.mix(color, Color.BLACK, 0.3)));
			g.fill(new Ellipse2D.Float(4, 4, s - 8, s - 8));
			g.dispose();
		}
	}

	/** A small on/off switch for a rule's card. */
	public static final class Switch extends JComponent
	{
		private boolean on;

		public Switch(boolean on, Consumer<Boolean> onToggle)
		{
			this.on = on;
			Dimension d = new Dimension(26, 14);
			setPreferredSize(d);
			setMinimumSize(d);
			setMaximumSize(d);
			setToolTipText(on ? "On: click to turn this rule off" : "Off: click to turn this rule on");
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseReleased(MouseEvent e)
				{
					Switch.this.on = !Switch.this.on;
					repaint();
					onToggle.accept(Switch.this.on);
					e.consume();
				}
			});
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			Theme.smooth(g);
			int w = getWidth();
			int h = getHeight();
			RoundRectangle2D track = new RoundRectangle2D.Float(0.5f, 0.5f, w - 1, h - 1, h, h);
			g.setColor(on ? Theme.alpha(Theme.GOLD, 90) : Theme.INK_DEEP);
			g.fill(track);
			g.setColor(on ? Theme.alpha(Theme.GOLD_BRIGHT, 200) : Theme.STROKE);
			g.draw(track);
			float knob = h - 4;
			float x = on ? w - knob - 2 : 2;
			g.setColor(on ? Theme.GOLD_BRIGHT : Theme.TEXT_FAINT);
			g.fill(new Ellipse2D.Float(x, 2, knob, knob));
			g.dispose();
		}
	}

	static String clip(String text, FontMetrics fm, int width)
	{
		if (fm.stringWidth(text) <= width)
		{
			return text;
		}
		int end = text.length();
		while (end > 0 && fm.stringWidth(text.substring(0, end) + "...") > width)
		{
			end--;
		}
		return text.substring(0, end) + "...";
	}
}
