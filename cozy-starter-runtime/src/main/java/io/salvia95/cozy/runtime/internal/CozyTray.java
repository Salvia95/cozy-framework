package io.salvia95.cozy.runtime.internal;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.Locale;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

/**
 * 시스템 트레이 아이콘: 열기, 종료 (PRD §9.2).
 *
 * <p>headless이거나 OS가 트레이를 지원하지 않으면 아무것도 하지 않는다. 그때는 화면 메뉴의 종료를 쓴다.
 * 아이콘은 앱 이름의 첫 글자를 그린 이미지다(별도 이미지 파일이 필요 없다).
 */
final class CozyTray {

	private static final Log logger = LogFactory.getLog(CozyTray.class);

	private @Nullable TrayIcon icon;

	/** @return 트레이에 올렸으면 true */
	boolean install(String appName, URI uri, CozyShutdown shutdown) {
		if (GraphicsEnvironment.isHeadless()) {
			return false;
		}
		try {
			if (!SystemTray.isSupported()) {
				logger.info("[cozy] 이 환경은 시스템 트레이를 지원하지 않습니다. 종료는 화면 메뉴에서 하세요.");
				return false;
			}
			SystemTray tray = SystemTray.getSystemTray();
			PopupMenu menu = new PopupMenu();
			MenuItem open = new MenuItem("열기");
			open.addActionListener((event) -> BrowserOpener.open(uri));
			MenuItem exit = new MenuItem("종료");
			exit.addActionListener((event) -> shutdown.request("트레이 메뉴", 0));
			menu.add(open);
			menu.addSeparator();
			menu.add(exit);
			TrayIcon trayIcon = new TrayIcon(image(appName, tray.getTrayIconSize().width), appName + " (" + uri + ")",
					menu);
			trayIcon.setImageAutoSize(true);
			trayIcon.addActionListener((event) -> BrowserOpener.open(uri));
			tray.add(trayIcon);
			this.icon = trayIcon;
			return true;
		}
		catch (Exception | LinkageError ex) {
			logger.warn("[cozy] 트레이 아이콘을 올릴 수 없습니다. 종료는 화면 메뉴에서 하세요: " + ex);
			return false;
		}
	}

	void remove() {
		TrayIcon trayIcon = this.icon;
		this.icon = null;
		if (trayIcon != null) {
			try {
				SystemTray.getSystemTray().remove(trayIcon);
			}
			catch (Exception | LinkageError ex) {
				logger.debug("트레이 아이콘을 내릴 수 없습니다", ex);
			}
		}
	}

	static Image image(String appName, int size) {
		int s = Math.max(16, size);
		BufferedImage image = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setColor(new Color(0x20, 0x6b, 0xc4));
			g.fillRoundRect(0, 0, s, s, s / 3, s / 3);
			String letter = appName.isBlank() ? "C" : appName.strip().substring(0, 1).toUpperCase(Locale.ROOT);
			g.setColor(Color.WHITE);
			g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) (s * 0.7)));
			int width = g.getFontMetrics().stringWidth(letter);
			int ascent = g.getFontMetrics().getAscent();
			int descent = g.getFontMetrics().getDescent();
			g.drawString(letter, (s - width) / 2, (s + ascent - descent) / 2);
		}
		finally {
			g.dispose();
		}
		return image;
	}
}
