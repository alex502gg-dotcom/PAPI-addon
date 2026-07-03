# PlayerReportsPAPI

Small PlaceholderAPI bridge for the PlayerReports plugin.

## Placeholders

- `%playerreports_reports%`
- `%playerreports_open%`
- `%playerreports_total%`
- `%playerreports_count%`

All aliases return the same value: the current report count.

## Install

1. Build with `mvn package`.
2. Put `target/playerreports-papi-1.0.0.jar` into `plugins/`.
3. Restart the server.
4. Use `%playerreports_reports%` in TAB or any PlaceholderAPI-compatible scoreboard.

## Config

If your reports plugin has a different plugin name or stores reports in another folder, edit `plugins/PlayerReportsPAPI/config.yml`.
