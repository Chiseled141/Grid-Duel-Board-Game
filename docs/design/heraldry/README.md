# Generated heraldry artwork

Created with the built-in image generation tool after internet research on 19 October 2026. These are artistic interpretations for review, not authenticated surviving banners. The Atlas uses them: shields on the map, picker and side panel, and banners as the side-panel header. The game loads small copies from `src/main/resources/assets/atlas/heraldry/` (240×360 PNG shields, 600×400 JPEG banners) with the notes in `heraldry.properties`. After changing a source image, regenerate the copies:

```sh
java tools/ResizeArt.java docs/design/heraldry/coats-of-arms src/main/resources/assets/atlas/heraldry/coats 240 360 png
java tools/ResizeArt.java docs/design/heraldry/flags src/main/resources/assets/atlas/heraldry/flags 600 400 jpg
```

`flags/` contains flat, edge-to-edge rectangular flag images. The approved format direction from the user is a full rectangle or square as appropriate to the flag, without cloth folds, waving, poles, or surrounding margins. `coats-of-arms/` contains separate painted heater shields on transparent backgrounds. Exact prompts are preserved in `prompts.json`.

## Design references and dating

| Faction | Selected design | Historical scope |
| --- | --- | --- |
| Hungary | Árpád red/silver stripes for the banner; striped and double-cross halves for the shield | Simplified medieval interpretations, not an exact reconstruction of Vladislaus I's personal royal standard |
| Poland | Crowned silver eagle displayed on red | Medieval royal heraldry |
| Bohemia | Crowned silver lion with two tails on red | Medieval royal heraldry |
| Austria | Red field with a silver horizontal fess | Medieval Bindenschild heraldry |
| Styria | Silver heraldic panther on green, red horns/claws and flame | Medieval ducal motif; simplified artistic drawing, not a facsimile of a particular armorial |
| Croatia | Silver/red checkerboard | Later medieval interpretation; the linked reference is dated 1495, not 1444. The flag is an artistic banner of arms, not a verified national flag for 1444 |
| Transylvania | Eagle, sun, crescent and seven towers, blue/gold fields separated by red | Later seventeenth-century interpretation; not suitable as a claim about 1444. The red dividing band is recorded on a 1666 thaler. The flag is an artistic banner of arms |

Croatia, Styria, Austria and Transylvania are retained as the game's faction names. This does not imply seven sovereign countries in 1444. Transylvania was then within the Kingdom of Hungary, and its familiar separate territorial arms developed later.

## Internet sources consulted before generation

- [Hungary: Árpád stripes](https://en.wikipedia.org/wiki/%C3%81rp%C3%A1d_stripes) and [development of the arms](https://en.wikipedia.org/wiki/Coat_of_arms_of_Hungary).
- [Vladislaus I royal banner reference](https://commons.wikimedia.org/wiki/File:Flag_of_Vladislaus_I_of_Hungary.svg), consulted for period context rather than copied as the selected striped banner.
- [Polish medieval royal banner](https://commons.wikimedia.org/wiki/File:Flag_of_the_Kingdom_of_Poland.svg).
- [Bohemian royal banner](https://commons.wikimedia.org/wiki/File:Royal_Banner_of_the_Kingdom_of_Bohemia.png) and [Czech diplomatic description of the Bohemian lion](https://mzv.gov.cz/dublin/en/about_the_czech_republic/czech_state_symbols/).
- [Austrian and Styrian medieval heraldry](https://www.earlyblazon.com/earlyblazon/nation/empire/carinthie.htm).
- [Universalmuseum Joanneum: Styrian panther](https://www.museum-joanneum.at/en/steiermarkschau/pavilions/alpine-adriatic-region-pavilion) and [Styrian arms history](https://en.wikipedia.org/wiki/Coat_of_arms_of_Styria).
- [Croatia/Dalmatia arms dated 1495](https://commons.wikimedia.org/wiki/File:Coat_of_arms_of_Croatia_and_Dalmatia_1495.svg). The Atlas previously showed the [Talovac campaign banner](https://commons.wikimedia.org/wiki/File:Talovac.svg), a different design; it and the other Wikimedia banners were replaced by this set (they remain in git history).
- [Historical Transylvanian arms and dating](https://cimerek.hu/en/coat-of-arms/historical-coat-of-arms-of-transylvania/) and [further history](https://en.wikipedia.org/wiki/Flag_and_coat_of_arms_of_Transylvania).

The generation used written heraldic descriptions, not downloaded source illustrations as image inputs. Inspect generated details before treating these images as authoritative heraldic references.
