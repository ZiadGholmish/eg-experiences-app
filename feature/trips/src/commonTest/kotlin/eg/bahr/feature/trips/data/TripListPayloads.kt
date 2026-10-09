package eg.bahr.feature.trips.data

/**
 * `GET /api/v1/trips` bodies in the shape the backend serves after `make seed` (bahr-be
 * scripts/seed-burullus.sql): the four Burullus trips, in English and in Arabic, built from the
 * openapi `TripPageEnvelope` / `TripCard` schemas. Null fields are omitted, as the backend omits
 * them; every trip has a hero and a card photo; only `burullus-dawn` has a rating; `facets` is not
 * served yet; tones are lowercase. The LQIPs are the seed's real 12-pixel PNGs.
 */
internal object TripListPayloads {
    val english: String =
        """
{
  "success": true,
  "data": {
    "items": [
      {
        "slug": "burullus-dawn",
        "title": "Dawn on Lake Burullus",
        "subtitle": "Lake Burullus · Kafr El Sheikh",
        "durationLabel": "05:00 → 22:00",
        "durationMinutes": 1020,
        "price": {
          "amount": 450,
          "currency": "EGP"
        },
        "categories": [
          "on_the_boat",
          "birds",
          "food"
        ],
        "heroImage": {
          "url": "http://localhost:9000/bahr-assets/seed/joy-boats.png",
          "width": 900,
          "height": 560,
          "alt": "Fishing boats on Lake Burullus",
          "lqip": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAHCAYAAAA8sqwkAAAAAXNSR0IArs4c6QAAAERlWElmTU0AKgAAAAgAAYdpAAQAAAABAAAAGgAAAAAAA6ABAAMAAAABAAEAAKACAAQAAAABAAAADKADAAQAAAABAAAABwAAAABk3MtMAAABGklEQVQYGSWQz0rDQBDGf5ts0iwtaeuh4EUQD6LiQXr17tVH8FF8An0fFa+l+OfSi3oQCq2ibSlNE9M2u1k3+g3zMczwDTOf6N482EBYAg9yI6hgXbrWH0nPIxCCtSmpQu7FdY7qmtOW5nYeMy8MidE0fJ/AaZQM6G7V6U8TvtYF8qRh6GjDJoWDhiTJV2TLnHbcJAwDZsmcZJazq0J2VA3/4nz/kv6Y+2FK1FHEwx7N3gsDY1E1zfbbHcunCaJdIxQ+4vD6yrKSFL7HWctS5gmPS8W3cmK/xOo1EYqN256VIEef7hb3VGQ1g+kPRni8iwKdpCwwbiY5lpZokjEqM6T5GCOcL7nj58oYl4jFv1NV7fBaCoy1bDzLL339dq9AAzQlAAAAAElFTkSuQmCC"
        },
        "cardImage": {
          "url": "http://localhost:9000/bahr-assets/seed/card-dawn.png",
          "width": 600,
          "height": 600,
          "alt": "The lake at dawn",
          "lqip": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAMCAYAAABWdVznAAAAAXNSR0IArs4c6QAAAERlWElmTU0AKgAAAAgAAYdpAAQAAAABAAAAGgAAAAAAA6ABAAMAAAABAAEAAKACAAQAAAABAAAADKADAAQAAAABAAAADAAAAAATDPpdAAABlUlEQVQoFV2Sv27UQBDGf/vHd459lygiIbmIo0CpQ8kD0ORJeAIKSrq0qVOkRynSAqJFCkVACDoKoEiQTvHlcueEi+31Mrs5CYGt0Yznm2/9zcyqndcfPPFR+Bh5lJJEiMU78VoCQfEC2LVu5y8aiYvKGMOy9ZRORWIg2c2lJDL/r22Fdy/x7G3XHJ5b3k80HQ32fpYuzvrXhf8kIunNxDLzhq1MiTQhDHPR1zQoaUCJRr3Q7bXGt5rvc01uWvo6HCGEp1/fMXm0TZPmXM8rirrmYWrILsaYfEx5PcDny6i+phWdZn/Fvcw2Lkg+/uD0U8XBaMSz4SrDk7f0s1PqV46jL5cU65bdBwPUzvMXnrTFV1q0ZjRLlsfrq/w8O0N3HU3ZYeSg0+uy2cuw324c+iaM3ZGqCU9E66CcM5sVfJ72aFQdm70dl1wVBdbMrjDSbFhQV4pXqt+o4heNS3GS1HGbMhB545T8dEorhLDcqdixMjiZjqHCMpdtBwTau2uA9WWJC4QFkAhoBfRircQhHyx8h+vyB+5vp3Uw8W6qAAAAAElFTkSuQmCC"
        },
        "nextDeparture": {
          "date": "2026-10-10",
          "seatsRemaining": 6,
          "capacity": 18,
          "soldOut": false
        },
        "badge": {
          "label": "Most booked",
          "tone": "primary"
        },
        "rating": {
          "value": 4.8,
          "count": 37
        },
        "location": {
          "lat": 31.503,
          "lng": 30.804,
          "label": "Burg El Burullus"
        }
      },
      {
        "slug": "reed-channels-kayak",
        "title": "Reed-cutters' channels, by kayak",
        "subtitle": "South shore · Lake Burullus",
        "durationLabel": "05:00 → 19:00",
        "durationMinutes": 840,
        "price": {
          "amount": 520,
          "currency": "EGP"
        },
        "categories": [
          "on_the_boat",
          "birds"
        ],
        "heroImage": {
          "url": "http://localhost:9000/bahr-assets/seed/joy-birds.png",
          "width": 900,
          "height": 560,
          "alt": "Birds among the reeds",
          "lqip": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAHCAYAAAA8sqwkAAAAAXNSR0IArs4c6QAAAERlWElmTU0AKgAAAAgAAYdpAAQAAAABAAAAGgAAAAAAA6ABAAMAAAABAAEAAKACAAQAAAABAAAADKADAAQAAAABAAAABwAAAABk3MtMAAAA10lEQVQYGV2QXU4DMQyEv8TJ8tdKQAXX4KKcC4TgBiC1L1Vf2W0BsckmTIpKJZJYsSe2ZxxXnu8r/1YD3AFzzROi09xAPz88/d3HZEFXlzDsICfVOMI4zSlyvcpLPZL9sjjq2S02GCWPeC+Gx2DkMhHx5DGTohO1w09lz1hWa2IXmczRmRGewkKd4a7/ZGMeq55BCRdp5P0kMkuJ6+R4O+1QDeFhs6XKWX0ooTNucqGPgS/JzP6bmeKFbHkuDWocXl6X+y+RWm0wWVGH6lokeZqrSWxRG/oHoZdW3yuBVIEAAAAASUVORK5CYII="
        },
        "cardImage": {
          "url": "http://localhost:9000/bahr-assets/seed/card-kayak.png",
          "width": 600,
          "height": 600,
          "alt": "A kayak in the reed channels",
          "lqip": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAMCAYAAABWdVznAAAAAXNSR0IArs4c6QAAAERlWElmTU0AKgAAAAgAAYdpAAQAAAABAAAAGgAAAAAAA6ABAAMAAAABAAEAAKACAAQAAAABAAAADKADAAQAAAABAAAADAAAAAATDPpdAAABjUlEQVQoFV2Ry2oUURCGv9OnJ31zYpxxNChZRBcu3AgBX8G38CHyBPoO2bpw48JNfAHJVhfiahACgogODCMxk+5Oz/TlHKt6jEgOVFGX/6+qU2X2Xh978Bj0bbQEMGqK4bzkekfzEE6ywRVuE/lPK/HWICBvHZ068sK729HGuqa18iSyHD3Z4ehrwclizVZghDDcEujf/tdIAwG8WzQssdyXwlZGC+/djDDSzuisuJ7yb26JTxtHnFn240DG8oRPL2aQtqyGOxT2Bl3VcidNyX3T0+PpOTPf0uwl7G7H2FfJ+sUkOyWuBnz+UPN+/ovDB/uMPp0wjr6QvV3x5uM38t2Y548eYp69PPQ+tbg6ZJnLROOYx7dH/Jx9xyaOukw46xzJMGacxISnlzK3CjVR4DioS0bFmvPuN9N5Rmfy/rNFVTKXP4X1mZbVJxuwDutL1tUPijKlKHwP1gv2Z1BCJQS9pC52Jfp4Ech1I0LTijQEklOwIjrXEa6Wl71jZOfOud7Wfq1II9ArgrK0zx8N8aD8+3SYXwAAAABJRU5ErkJggg=="
        },
        "nextDeparture": {
          "date": "2026-10-10",
          "seatsRemaining": 13,
          "capacity": 18,
          "soldOut": false
        },
        "badge": {
          "label": "Flamingo season",
          "tone": "secondary"
        },
        "location": {
          "lat": 31.443,
          "lng": 30.738,
          "label": "South shore, Lake Burullus"
        }
      },
      {
        "slug": "burullus-murals",
        "title": "Murals of Burg El Burullus, on foot",
        "subtitle": "Village · Burg El Burullus",
        "durationLabel": "05:00 → 14:00",
        "durationMinutes": 540,
        "price": {
          "amount": 220,
          "currency": "EGP"
        },
        "categories": [
          "murals"
        ],
        "heroImage": {
          "url": "http://localhost:9000/bahr-assets/seed/joy-mural.png",
          "width": 900,
          "height": 560,
          "alt": "A painted house in Burg El Burullus",
          "lqip": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAHCAYAAAA8sqwkAAAAAXNSR0IArs4c6QAAAERlWElmTU0AKgAAAAgAAYdpAAQAAAABAAAAGgAAAAAAA6ABAAMAAAABAAEAAKACAAQAAAABAAAADKADAAQAAAABAAAABwAAAABk3MtMAAABG0lEQVQYGU2QTUrDUBSFv/ySpqQai6AOnDkRiiNdgUtwB67FdbgGBzoVHAnioAqCCkqtLSj5aZM06et7yTM68gzOud+9gwvHOL4eagPQRuuGbocW/uuX25P+M4196i1RdUNBm4FPuqzJ6y5NuwulTceyiIyKoGvwHKfYJzsbfGcFdvlBL1zjMVkxsdapCsGgNtjs+dxnKQd9zdn7GFu/DHHLJfgJ7lqMKl1uZz6WqNkXAeSKsUzJHElcLbDzmyssBZNBH5MR42KPi1dB37Q5+uwwm0seDjVPUYZWCtPKFiQRdEXKXaRYiTlbHQfP1DRZglNO268Vu2qEVBLj7fJcC+nihJKvtgnHNMmaENk0bOcCt5ZMAw/Pzogbnx8ipoxQivhOgwAAAABJRU5ErkJggg=="
        },
        "cardImage": {
          "url": "http://localhost:9000/bahr-assets/seed/card-mural.png",
          "width": 600,
          "height": 600,
          "alt": "The village murals",
          "lqip": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAMCAYAAABWdVznAAAAAXNSR0IArs4c6QAAAERlWElmTU0AKgAAAAgAAYdpAAQAAAABAAAAGgAAAAAAA6ABAAMAAAABAAEAAKACAAQAAAABAAAADKADAAQAAAABAAAADAAAAAATDPpdAAABjUlEQVQoFW2Rz04UQRDGfzXTzDCDy4qGGNcDQiDxJXwCr1w98hrGF9CXkGfQg4mGePCiQQ1BA4SDLHBhIcvuwuy63WX17O7FWOk/qarv6+r6Sp5++K5g678mqFpO6mUIwT0u85k34VlyZvGZRqrceCFEx3JuvVHM8vUd8TEX92Ia2Gp1edtZYG+Q4SJh4x9CzYqHMVJRDoYJZZbxJElJjCCfOl1FBdFgqGBVY3mN1esq3qXkYlHv+WP/cq3zI7Qco81lKtekurllaT5nbCAkYfSjzUmomFu5x0pzkfSVXLzM3TeS64SPO5dsH//ieatJ+fk9ObsM3pzxeucr7aXA5uoj5N2zF6qlww9Tuj2lelCw9vAu1dEpLATG/YJO8GSNee5bZbc78ojt+OMsC7TmevQY0JMLTjt3rL2RNW/zuOzz21Dup15N5yDWXMDfDijaY86k4ER9rYxpMZXACPtGEJIYwTrly1WcbpRwaC9Xpl7Ua2IhBNyh9i0IiYkcpuM0ipEMaFKLKWXeRGbD/QVzCaCTinu46wAAAABJRU5ErkJggg=="
        },
        "nextDeparture": {
          "date": "2026-10-10",
          "seatsRemaining": 16,
          "capacity": 18,
          "soldOut": false
        },
        "badge": {
          "label": "Half day",
          "tone": "quaternary"
        },
        "location": {
          "lat": 31.5905,
          "lng": 30.9905,
          "label": "The murals of Burg El Burullus"
        }
      },
      {
        "slug": "boughaz-fish-market",
        "title": "The boughaz and the morning fish market",
        "subtitle": "Burg El Burullus · Kafr El Sheikh",
        "durationLabel": "05:00 → 17:00",
        "durationMinutes": 720,
        "price": {
          "amount": 380,
          "currency": "EGP"
        },
        "categories": [
          "on_the_boat",
          "food"
        ],
        "heroImage": {
          "url": "http://localhost:9000/bahr-assets/seed/joy-market.png",
          "width": 900,
          "height": 560,
          "alt": "The fish market at Burg El Burullus",
          "lqip": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAHCAYAAAA8sqwkAAAAAXNSR0IArs4c6QAAAERlWElmTU0AKgAAAAgAAYdpAAQAAAABAAAAGgAAAAAAA6ABAAMAAAABAAEAAKACAAQAAAABAAAADKADAAQAAAABAAAABwAAAABk3MtMAAAA+UlEQVQYGSWPQU7DQAxF30wmTSlBVSELhGBB10hchjtwH27EAhZd9QIVEgsCgkAJadK0yQx2Y8myZ/73/7ZpXx8DiyXEMVjLIbyXEsCY4S9ILxHlOY6PBcx2AgioBA0laOpbU/lSeuNx7q/ExxFWCCZ4wcRFh0M/zBo76KjAicV9lfCUzLlufrmIjnDjTzh25PUIK5udNx1hZCmyS85+Vri6qFhtX0hrEf6ekc1z4umG580Vtjfcrd9YTzOW6Q23Im7eH+5DKe6JN0T7SBx22MhT+FhWhNPQ0VtHlaRM+gbXlS0TWVvv0tyLkzYpoiKntIdLOsbV9oD/A4JEY4LKBVk6AAAAAElFTkSuQmCC"
        },
        "cardImage": {
          "url": "http://localhost:9000/bahr-assets/seed/card-market.png",
          "width": 600,
          "height": 600,
          "alt": "Fresh fish at the market",
          "lqip": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAMCAYAAABWdVznAAAAAXNSR0IArs4c6QAAAERlWElmTU0AKgAAAAgAAYdpAAQAAAABAAAAGgAAAAAAA6ABAAMAAAABAAEAAKACAAQAAAABAAAADKADAAQAAAABAAAADAAAAAATDPpdAAABaUlEQVQoFW2Rv04bQRDGf7u3PstnEREiJAqUiiY0PAJ9ijwSfarUPAINbdq8QYDCFJasEAQWf+QcxuA727vDzPk6Mne7O/N93+zM7ro0+C443pmIQoo7XcVWC3UK1Kt3YgNM0FiW4+JSXdHPEl4tod2uUbSTlch7sHcIf89hcg0+04S5Zf/HbI/6FUYX8PSgftSyibCaLdYFlHdtw6ZtejKnvFTfG2kooXqYIZlDkifqIOguKeFjJHW95jm8nloUi4Z3z26huqIczzn5tcOPl1385hcY3lHLLZNBzvGfDqdhn3DwDffz65FIAXGZMa1y4laHz58KZqMx9LTqS5fHHDq9Htv9gvB7ucJNtRWJ+LAkFBvM+olREfg4XZCxwldKz5+5eRTCJSXWqd2yuIyyXlCNJ3yQBfdJld5YNZ3Ea8IglXoBGhla66PfGCv8U0x8e2B7k/VPGKapatsE05qro7natPYbXikNeQO5v5tCMRVSyAAAAABJRU5ErkJggg=="
        },
        "nextDeparture": {
          "date": "2026-10-10",
          "seatsRemaining": 14,
          "capacity": 18,
          "soldOut": false
        },
        "badge": {
          "label": "Starts 06:00",
          "tone": "tertiary"
        },
        "location": {
          "lat": 31.586,
          "lng": 30.981,
          "label": "The boughaz and the fish market"
        }
      }
    ],
    "page": 0,
    "size": 20,
    "totalItems": 4,
    "totalPages": 1
  }
}
        """.trimIndent()

    val arabic: String =
        """
{
  "success": true,
  "data": {
    "items": [
      {
        "slug": "burullus-dawn",
        "title": "الفجر على بحيرة البرلس",
        "subtitle": "بحيرة البرلس · كفر الشيخ",
        "durationLabel": "05:00 → 22:00",
        "durationMinutes": 1020,
        "price": {
          "amount": 450,
          "currency": "EGP"
        },
        "categories": [
          "on_the_boat",
          "birds",
          "food"
        ],
        "heroImage": {
          "url": "http://localhost:9000/bahr-assets/seed/joy-boats.png",
          "width": 900,
          "height": 560,
          "alt": "قوارب الصيد على بحيرة البرلس",
          "lqip": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAHCAYAAAA8sqwkAAAAAXNSR0IArs4c6QAAAERlWElmTU0AKgAAAAgAAYdpAAQAAAABAAAAGgAAAAAAA6ABAAMAAAABAAEAAKACAAQAAAABAAAADKADAAQAAAABAAAABwAAAABk3MtMAAABGklEQVQYGSWQz0rDQBDGf5ts0iwtaeuh4EUQD6LiQXr17tVH8FF8An0fFa+l+OfSi3oQCq2ibSlNE9M2u1k3+g3zMczwDTOf6N482EBYAg9yI6hgXbrWH0nPIxCCtSmpQu7FdY7qmtOW5nYeMy8MidE0fJ/AaZQM6G7V6U8TvtYF8qRh6GjDJoWDhiTJV2TLnHbcJAwDZsmcZJazq0J2VA3/4nz/kv6Y+2FK1FHEwx7N3gsDY1E1zfbbHcunCaJdIxQ+4vD6yrKSFL7HWctS5gmPS8W3cmK/xOo1EYqN256VIEef7hb3VGQ1g+kPRni8iwKdpCwwbiY5lpZokjEqM6T5GCOcL7nj58oYl4jFv1NV7fBaCoy1bDzLL339dq9AAzQlAAAAAElFTkSuQmCC"
        },
        "cardImage": {
          "url": "http://localhost:9000/bahr-assets/seed/card-dawn.png",
          "width": 600,
          "height": 600,
          "alt": "البحيرة وقت الفجر",
          "lqip": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAMCAYAAABWdVznAAAAAXNSR0IArs4c6QAAAERlWElmTU0AKgAAAAgAAYdpAAQAAAABAAAAGgAAAAAAA6ABAAMAAAABAAEAAKACAAQAAAABAAAADKADAAQAAAABAAAADAAAAAATDPpdAAABlUlEQVQoFV2Sv27UQBDGf/vHd459lygiIbmIo0CpQ8kD0ORJeAIKSrq0qVOkRynSAqJFCkVACDoKoEiQTvHlcueEi+31Mrs5CYGt0Yznm2/9zcyqndcfPPFR+Bh5lJJEiMU78VoCQfEC2LVu5y8aiYvKGMOy9ZRORWIg2c2lJDL/r22Fdy/x7G3XHJ5b3k80HQ32fpYuzvrXhf8kIunNxDLzhq1MiTQhDHPR1zQoaUCJRr3Q7bXGt5rvc01uWvo6HCGEp1/fMXm0TZPmXM8rirrmYWrILsaYfEx5PcDny6i+phWdZn/Fvcw2Lkg+/uD0U8XBaMSz4SrDk7f0s1PqV46jL5cU65bdBwPUzvMXnrTFV1q0ZjRLlsfrq/w8O0N3HU3ZYeSg0+uy2cuw324c+iaM3ZGqCU9E66CcM5sVfJ72aFQdm70dl1wVBdbMrjDSbFhQV4pXqt+o4heNS3GS1HGbMhB545T8dEorhLDcqdixMjiZjqHCMpdtBwTau2uA9WWJC4QFkAhoBfRircQhHyx8h+vyB+5vp3Uw8W6qAAAAAElFTkSuQmCC"
        },
        "nextDeparture": {
          "date": "2026-10-10",
          "seatsRemaining": 6,
          "capacity": 18,
          "soldOut": false
        },
        "badge": {
          "label": "الأكثر حجزًا",
          "tone": "primary"
        },
        "rating": {
          "value": 4.8,
          "count": 37
        },
        "location": {
          "lat": 31.503,
          "lng": 30.804,
          "label": "برج البرلس"
        }
      },
      {
        "slug": "reed-channels-kayak",
        "title": "قنوات قطّاعي البوص بالكياك",
        "subtitle": "الشاطئ الجنوبي · بحيرة البرلس",
        "durationLabel": "05:00 → 19:00",
        "durationMinutes": 840,
        "price": {
          "amount": 520,
          "currency": "EGP"
        },
        "categories": [
          "on_the_boat",
          "birds"
        ],
        "heroImage": {
          "url": "http://localhost:9000/bahr-assets/seed/joy-birds.png",
          "width": 900,
          "height": 560,
          "alt": "طيور بين البوص",
          "lqip": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAHCAYAAAA8sqwkAAAAAXNSR0IArs4c6QAAAERlWElmTU0AKgAAAAgAAYdpAAQAAAABAAAAGgAAAAAAA6ABAAMAAAABAAEAAKACAAQAAAABAAAADKADAAQAAAABAAAABwAAAABk3MtMAAAA10lEQVQYGV2QXU4DMQyEv8TJ8tdKQAXX4KKcC4TgBiC1L1Vf2W0BsckmTIpKJZJYsSe2ZxxXnu8r/1YD3AFzzROi09xAPz88/d3HZEFXlzDsICfVOMI4zSlyvcpLPZL9sjjq2S02GCWPeC+Gx2DkMhHx5DGTohO1w09lz1hWa2IXmczRmRGewkKd4a7/ZGMeq55BCRdp5P0kMkuJ6+R4O+1QDeFhs6XKWX0ooTNucqGPgS/JzP6bmeKFbHkuDWocXl6X+y+RWm0wWVGH6lokeZqrSWxRG/oHoZdW3yuBVIEAAAAASUVORK5CYII="
        },
        "cardImage": {
          "url": "http://localhost:9000/bahr-assets/seed/card-kayak.png",
          "width": 600,
          "height": 600,
          "alt": "كياك في قنوات البوص",
          "lqip": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAMCAYAAABWdVznAAAAAXNSR0IArs4c6QAAAERlWElmTU0AKgAAAAgAAYdpAAQAAAABAAAAGgAAAAAAA6ABAAMAAAABAAEAAKACAAQAAAABAAAADKADAAQAAAABAAAADAAAAAATDPpdAAABjUlEQVQoFV2Ry2oUURCGv9OnJ31zYpxxNChZRBcu3AgBX8G38CHyBPoO2bpw48JNfAHJVhfiahACgogODCMxk+5Oz/TlHKt6jEgOVFGX/6+qU2X2Xh978Bj0bbQEMGqK4bzkekfzEE6ywRVuE/lPK/HWICBvHZ068sK729HGuqa18iSyHD3Z4ehrwclizVZghDDcEujf/tdIAwG8WzQssdyXwlZGC+/djDDSzuisuJ7yb26JTxtHnFn240DG8oRPL2aQtqyGOxT2Bl3VcidNyX3T0+PpOTPf0uwl7G7H2FfJ+sUkOyWuBnz+UPN+/ovDB/uMPp0wjr6QvV3x5uM38t2Y548eYp69PPQ+tbg6ZJnLROOYx7dH/Jx9xyaOukw46xzJMGacxISnlzK3CjVR4DioS0bFmvPuN9N5Rmfy/rNFVTKXP4X1mZbVJxuwDutL1tUPijKlKHwP1gv2Z1BCJQS9pC52Jfp4Ech1I0LTijQEklOwIjrXEa6Wl71jZOfOud7Wfq1II9ArgrK0zx8N8aD8+3SYXwAAAABJRU5ErkJggg=="
        },
        "nextDeparture": {
          "date": "2026-10-10",
          "seatsRemaining": 13,
          "capacity": 18,
          "soldOut": false
        },
        "badge": {
          "label": "موسم الفلامنجو",
          "tone": "secondary"
        },
        "location": {
          "lat": 31.443,
          "lng": 30.738,
          "label": "الشاطئ الجنوبي لبحيرة البرلس"
        }
      },
      {
        "slug": "burullus-murals",
        "title": "جداريات برج البرلس على رجليك",
        "subtitle": "القرية · برج البرلس",
        "durationLabel": "05:00 → 14:00",
        "durationMinutes": 540,
        "price": {
          "amount": 220,
          "currency": "EGP"
        },
        "categories": [
          "murals"
        ],
        "heroImage": {
          "url": "http://localhost:9000/bahr-assets/seed/joy-mural.png",
          "width": 900,
          "height": 560,
          "alt": "جدارية على بيت في برج البرلس",
          "lqip": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAHCAYAAAA8sqwkAAAAAXNSR0IArs4c6QAAAERlWElmTU0AKgAAAAgAAYdpAAQAAAABAAAAGgAAAAAAA6ABAAMAAAABAAEAAKACAAQAAAABAAAADKADAAQAAAABAAAABwAAAABk3MtMAAABG0lEQVQYGU2QTUrDUBSFv/ySpqQai6AOnDkRiiNdgUtwB67FdbgGBzoVHAnioAqCCkqtLSj5aZM06et7yTM68gzOud+9gwvHOL4eagPQRuuGbocW/uuX25P+M4196i1RdUNBm4FPuqzJ6y5NuwulTceyiIyKoGvwHKfYJzsbfGcFdvlBL1zjMVkxsdapCsGgNtjs+dxnKQd9zdn7GFu/DHHLJfgJ7lqMKl1uZz6WqNkXAeSKsUzJHElcLbDzmyssBZNBH5MR42KPi1dB37Q5+uwwm0seDjVPUYZWCtPKFiQRdEXKXaRYiTlbHQfP1DRZglNO268Vu2qEVBLj7fJcC+nihJKvtgnHNMmaENk0bOcCt5ZMAw/Pzogbnx8ipoxQivhOgwAAAABJRU5ErkJggg=="
        },
        "cardImage": {
          "url": "http://localhost:9000/bahr-assets/seed/card-mural.png",
          "width": 600,
          "height": 600,
          "alt": "جداريات القرية",
          "lqip": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAMCAYAAABWdVznAAAAAXNSR0IArs4c6QAAAERlWElmTU0AKgAAAAgAAYdpAAQAAAABAAAAGgAAAAAAA6ABAAMAAAABAAEAAKACAAQAAAABAAAADKADAAQAAAABAAAADAAAAAATDPpdAAABjUlEQVQoFW2Rz04UQRDGfzXTzDCDy4qGGNcDQiDxJXwCr1w98hrGF9CXkGfQg4mGePCiQQ1BA4SDLHBhIcvuwuy63WX17O7FWOk/qarv6+r6Sp5++K5g678mqFpO6mUIwT0u85k34VlyZvGZRqrceCFEx3JuvVHM8vUd8TEX92Ia2Gp1edtZYG+Q4SJh4x9CzYqHMVJRDoYJZZbxJElJjCCfOl1FBdFgqGBVY3mN1esq3qXkYlHv+WP/cq3zI7Qco81lKtekurllaT5nbCAkYfSjzUmomFu5x0pzkfSVXLzM3TeS64SPO5dsH//ieatJ+fk9ObsM3pzxeucr7aXA5uoj5N2zF6qlww9Tuj2lelCw9vAu1dEpLATG/YJO8GSNee5bZbc78ojt+OMsC7TmevQY0JMLTjt3rL2RNW/zuOzz21Dup15N5yDWXMDfDijaY86k4ER9rYxpMZXACPtGEJIYwTrly1WcbpRwaC9Xpl7Ua2IhBNyh9i0IiYkcpuM0ipEMaFKLKWXeRGbD/QVzCaCTinu46wAAAABJRU5ErkJggg=="
        },
        "nextDeparture": {
          "date": "2026-10-10",
          "seatsRemaining": 16,
          "capacity": 18,
          "soldOut": false
        },
        "badge": {
          "label": "نص يوم",
          "tone": "quaternary"
        },
        "location": {
          "lat": 31.5905,
          "lng": 30.9905,
          "label": "جداريات برج البرلس"
        }
      },
      {
        "slug": "boughaz-fish-market",
        "title": "البوغاز وسوق السمك الصبح",
        "subtitle": "برج البرلس · كفر الشيخ",
        "durationLabel": "05:00 → 17:00",
        "durationMinutes": 720,
        "price": {
          "amount": 380,
          "currency": "EGP"
        },
        "categories": [
          "on_the_boat",
          "food"
        ],
        "heroImage": {
          "url": "http://localhost:9000/bahr-assets/seed/joy-market.png",
          "width": 900,
          "height": 560,
          "alt": "سوق السمك في برج البرلس",
          "lqip": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAHCAYAAAA8sqwkAAAAAXNSR0IArs4c6QAAAERlWElmTU0AKgAAAAgAAYdpAAQAAAABAAAAGgAAAAAAA6ABAAMAAAABAAEAAKACAAQAAAABAAAADKADAAQAAAABAAAABwAAAABk3MtMAAAA+UlEQVQYGSWPQU7DQAxF30wmTSlBVSELhGBB10hchjtwH27EAhZd9QIVEgsCgkAJadK0yQx2Y8myZ/73/7ZpXx8DiyXEMVjLIbyXEsCY4S9ILxHlOY6PBcx2AgioBA0laOpbU/lSeuNx7q/ExxFWCCZ4wcRFh0M/zBo76KjAicV9lfCUzLlufrmIjnDjTzh25PUIK5udNx1hZCmyS85+Vri6qFhtX0hrEf6ekc1z4umG580Vtjfcrd9YTzOW6Q23Im7eH+5DKe6JN0T7SBx22MhT+FhWhNPQ0VtHlaRM+gbXlS0TWVvv0tyLkzYpoiKntIdLOsbV9oD/A4JEY4LKBVk6AAAAAElFTkSuQmCC"
        },
        "cardImage": {
          "url": "http://localhost:9000/bahr-assets/seed/card-market.png",
          "width": 600,
          "height": 600,
          "alt": "السمك الطازة في السوق",
          "lqip": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAwAAAAMCAYAAABWdVznAAAAAXNSR0IArs4c6QAAAERlWElmTU0AKgAAAAgAAYdpAAQAAAABAAAAGgAAAAAAA6ABAAMAAAABAAEAAKACAAQAAAABAAAADKADAAQAAAABAAAADAAAAAATDPpdAAABaUlEQVQoFW2Rv04bQRDGf7u3PstnEREiJAqUiiY0PAJ9ijwSfarUPAINbdq8QYDCFJasEAQWf+QcxuA727vDzPk6Mne7O/N93+zM7ro0+C443pmIQoo7XcVWC3UK1Kt3YgNM0FiW4+JSXdHPEl4tod2uUbSTlch7sHcIf89hcg0+04S5Zf/HbI/6FUYX8PSgftSyibCaLdYFlHdtw6ZtejKnvFTfG2kooXqYIZlDkifqIOguKeFjJHW95jm8nloUi4Z3z26huqIczzn5tcOPl1385hcY3lHLLZNBzvGfDqdhn3DwDffz65FIAXGZMa1y4laHz58KZqMx9LTqS5fHHDq9Htv9gvB7ucJNtRWJ+LAkFBvM+olREfg4XZCxwldKz5+5eRTCJSXWqd2yuIyyXlCNJ3yQBfdJld5YNZ3Ea8IglXoBGhla66PfGCv8U0x8e2B7k/VPGKapatsE05qro7natPYbXikNeQO5v5tCMRVSyAAAAABJRU5ErkJggg=="
        },
        "nextDeparture": {
          "date": "2026-10-10",
          "seatsRemaining": 14,
          "capacity": 18,
          "soldOut": false
        },
        "badge": {
          "label": "تبدأ 06:00",
          "tone": "tertiary"
        },
        "location": {
          "lat": 31.586,
          "lng": 30.981,
          "label": "البوغاز وسوق السمك"
        }
      }
    ],
    "page": 0,
    "size": 20,
    "totalItems": 4,
    "totalPages": 1
  }
}
        """.trimIndent()
}
