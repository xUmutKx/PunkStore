# Changelog

## 0.49
- The bottom bar follows the Steam mobile app: labelled tabs, and tag, news, shield, bell and menu icons.
- A Menu page, and the profile opens from your avatar.
- The top bar shows the PUNK STORE name on one line.

## 0.48
- Steam library from SteamDB: games whose name came out wrong or empty ("App 123", a number or an hours line) now get their real title from the Steam store.

## 0.45
- Search: if Google Play search misses an app (Clash Royale, for one), I now also run the Play web search and check well-known package names, so it shows up.
- Search also looks at apps already installed on the phone that aren't in a catalog.

## 0.41
- "by UmutK" now only lists my own apps. Other GitHub projects (ReVanced Manager, Morphe, root tools) moved to "GitHub sources" in the Store quick row.
- GitHub apps now show as installed, and Open/Uninstall work, because the real package name is read from the downloaded APK.

## 0.40
- New "by UmutK" tab: my GitHub releases, plus a curated list of GitHub-only apps (ReVanced Manager, Morphe, Magisk, KernelSU, LSPosed, APatch and more). You can add any owner/repo yourself.
- Bottom tabs can be reordered in Settings.
- Steam app pages got more sections: features, a ratings graph, DLC, news, art and banners, Points Shop link and similar games.
- Google sign-in no longer gets a 403 (the embedded page now identifies as a normal browser).
- Steam sign-in keeps checking for the session cookie, so it finishes even when Steam's login page never reports "loaded".
- Downloads: the newest download is on top. The Steam account defaults to xUmutKx.

## 0.26
- The install button is a plain flat green again, and the panel behind it is a flat Steam box instead of a glossy gradient.
- The corner of the discount box next to it was cut off. Fixed.
- The PUNK STORE banner on the home page no longer opens anything. It just reacts to touch: a light follows your finger, it squishes a little, and the colour changes on every press.

## 0.25
- The profile page in the Steam themes now follows the Steam app's "You" page: maroon header with a square framed avatar, three stat boxes, a big blue button and plain "My content" rows.
- The big PUNK STORE banner on the home page is touchable: a light follows your finger, the colour shifts while you hold it, the gear spins, and a tap opens the app of the day.
- Screenshots added to the README.

## 0.24
- The install button is now a translucent liquid-glass button. Hold it and the colour drifts and it squishes like slime.
- Download progress lives in one thin bar above the bottom bar, on every screen. The second bar on the app page is gone.
- Notifications walk through Downloading, Installing and Installed.
- App pages keep the top and bottom bars. Links open inside the app instead of the browser.
- Install button first on the app page, with the Steam-only sections (players, requirements, languages) below.
- Metacritic-style yellow and black score boxes for Metacritic, Steam and Google Play, with verdicts and review counts.
- Steam requests carry an age-check cookie, so VR and mature games load without signing in.
- The Updates tab is now Downloads: active downloads, updates, and every installed app in a compact list (switchable).
- Optional floating dock in Settings (off by default). Smaller bottom bar labels. Search and Filters shortcuts removed from the home page.

## 0.23
- Steam stopped showing games lists to signed-out visitors, which is why linked accounts came up empty. Added **Sign in with Steam** (Steam's own login page, only the session cookie is kept), which brings in the full games list, playtime and achievements. Without signing in you still get the games visible on your profile, plus game, friend and badge counts. The app now tells you why a list is incomplete.
- Steam games show up in the library with their own filter, playtime and a "Playtime" sort.
- The Steam account page now looks like the Steam app's account page: profile header, stat boxes, a big blue button, a "My games" list and account rows.
- Settings redone for the Steam themes: large rows, grey section headers, blue checkmarks and switches.
- English is now the default language.
- Hours are read correctly no matter which number format Steam uses.
- The bag in the icon is 5% smaller.

## 0.22
- New download engine: a queue (two at a time), resume after pause, connection loss or app restart, automatic retries, a free space check, size and SHA-256 checks, and readable install errors. If an install gets cancelled the APK is kept, so retrying doesn't download it again.
- Per-app notifications with speed, time left, and pause and cancel buttons.
- The install button fills up as the download goes and walks through Queued → % → Verifying → Installing → Installed. Tap it to pause or resume.
- The library was rebuilt in Steam's style: last played card, recent row, filter chips with counts, collapsible groups, grid or list, five sort options, and a long-press menu. Apps installed elsewhere are listed too.
- Animated transitions between tabs and pages, and springy buttons.
- The icon's bag moved up so it sits centered.

## 0.21
- Icon: smaller bag, bigger gear.

## 0.20
- Uninstall from anywhere, the privacy report and reviews moved under the install panel, Google Play reviews, a bigger catalog that stays cached, a new icon (a gear inside a shopping bag), a new updates tab icon, and splash images cached in the background.

## 0.17 – 0.18
- Link Steam with just a profile ID (no API key), an empty search shows filter results, Steam-style search overlay with recent searches, a smaller button block on app pages.

## 0.16
- Live search with better ranking, ABI filtering for split APKs, a tidy button grid, real platform logos, reviews at the bottom, readable error messages, and a splash screen that no longer uses your icon pack.

## 0.15
- Fewer duplicate menu entries, badges and achievements merged, review retry, ABI-matched versions, Steam platform icons and SteamSpy estimates, and a privacy report.

## 0.14
- Fixed a crash on some Play app pages, app icons in notifications, reviews, a SteamDB section, system requirements, a gallery, Discover categories, and export/import of settings.

## 0.13 and earlier
- Steam colors sampled from real screenshots, a foreground download service, the Russo One logo, Steam store integration, theme textures, achievements, the about page, update checks, filters, Discover, install methods, Google sign-in, the wishlist and profile.
