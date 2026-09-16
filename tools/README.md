# tools

제품이 쓰는 에셋을 만드는 스크립트. 서버 코드는 아니다.

## massing.py — OSM → 축측투상 매싱 SVG

`static/img/mass_*.svg` 와 `mass_*.json` 을 만든다.
히어로의 미니어처 도시가 이 결과물이다.

```bash
cd tools
python massing.py          # 해운대 한 장을 새로 만든다
```

만들어진 `mass_haeundae.svg` · `mass_haeundae.json` 을
`src/main/resources/static/img/` 로 옮긴다.

### 다른 지역을 만들려면

파일 맨 아래 `build(...)` 호출을 고친다.

```python
build('gwangalli',                       # 파일 이름 (mass_gwangalli.svg)
      (35.1505, 129.1440, 35.1625, 129.1665),   # bbox: 남, 서, 북, 동
      16,                                 # 확대 (클수록 크게)
      0.74,                               # 세로 눌림 (축측투상 기울기)
      marks=[...])                        # 핀 찍을 곳
```

`marks` 한 줄의 모양:

```python
dict(name='해운대해수욕장', note='집중률 142',
     lat=35.15760, lon=129.15760,
     ground=True,          # 건물이 아니라 땅에 찍는다
     kind='hot',           # stop · stay · hot
     dy=-46)               # 말풍선 세로 보정
```

### 알아 둘 것

- OSM 응답은 `tools/osm_cache/` 에 저장된다. 같은 bbox 를 다시 부르면
  네트워크를 타지 않는다. 새로 받고 싶으면 그 폴더를 지운다
- Overpass 는 공용 서버라 느리거나 막힐 때가 있다. 한 번에 큰 bbox 를
  부르지 말 것
- **SVG 는 색을 갖지 않는다.** 클래스만 내보내고 색은
  `static/css/styles_massing.css` + `tokens.css` 가 정한다.
  그래야 시간대에 따라 낮·노을·밤으로 갈아끼울 수 있다
- SVG filter 를 쓰지 않는다. 그림자는 오프셋 두 겹으로 낸다
