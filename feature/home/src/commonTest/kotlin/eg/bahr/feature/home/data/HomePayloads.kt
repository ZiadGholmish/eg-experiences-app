package eg.bahr.feature.home.data

/**
 * `GET /api/v1/home` in the contract's shape (openapi `HomeEnvelope`), modelled on the seeded Home
 * (`bahr-be/scripts/seed-home.sql`): the 45:28 hero carousel with a `trip`, a `category` and a `none`
 * banner, the featured row (a day trip and the overnight white-desert trip, M4-B0b), the category
 * chips. Plus what a newer server may send: a section of a type this build does not know, a `trips`
 * row with one card this build cannot read (no required `slug`: only that card is dropped), and a
 * `trips` section this build cannot read at all (no required `id`).
 */
internal object HomePayloads {
    const val ENGLISH = """
{
  "success": true,
  "data": {
    "sections": [
      {
        "id": "0199c3a0-5eed-7000-8000-000000000701",
        "type": "banners",
        "layout": "carousel",
        "aspectRatio": "45:28",
        "items": [
          {
            "id": "0199c3a0-5eed-7000-8000-000000000801",
            "title": "Dawn on Lake Burullus",
            "image": { "url": "https://cdn.bahr.eg/seed/joy-boats.png", "width": 900, "height": 560, "lqip": "data:image/png;base64,AAAA" },
            "action": { "type": "trip", "value": "burullus-dawn" }
          },
          {
            "id": "0199c3a0-5eed-7000-8000-000000000802",
            "title": "Flamingo season",
            "image": { "url": "https://cdn.bahr.eg/seed/joy-birds.png", "width": 900, "height": 560 },
            "action": { "type": "category", "value": "birds" }
          },
          {
            "id": "0199c3a0-5eed-7000-8000-000000000803",
            "image": { "url": "https://cdn.bahr.eg/seed/joy-market.png", "width": 900, "height": 560 },
            "action": { "type": "none" },
            "sponsor": "a field a newer server added"
          }
        ]
      },
      {
        "id": "0199c3a0-5eed-7000-8000-000000000702",
        "type": "trips",
        "title": "Featured trips",
        "layout": "row",
        "totalItems": 12,
        "seeAll": { "type": "section", "value": "0199c3a0-5eed-7000-8000-000000000702" },
        "items": [
          {
            "slug": "burullus-dawn",
            "title": "Dawn on Lake Burullus",
            "durationLabel": "05:00 → 22:00",
            "durationMinutes": 1020,
            "nights": 0,
            "price": { "amount": 450, "currency": "EGP" },
            "categories": ["on_the_boat"],
            "nextDeparture": { "date": "2026-10-17", "returnDate": "2026-10-17", "seatsRemaining": 6, "capacity": 18, "soldOut": false }
          },
          {
            "slug": "white-desert-overnight",
            "title": "The White Desert, overnight",
            "durationLabel": "2 days · 1 night",
            "nights": 1,
            "price": { "amount": 2400, "currency": "EGP" },
            "nextDeparture": { "date": "2026-10-15", "returnDate": "2026-10-16", "seatsRemaining": 0, "capacity": 14, "soldOut": true }
          }
        ]
      },
      {
        "id": "0199c3a0-5eed-7000-8000-000000000799",
        "type": "stories",
        "layout": "row",
        "items": [ { "id": "s1", "video": "https://cdn.bahr.eg/s1.mp4" } ]
      },
      {
        "id": "0199c3a0-5eed-7000-8000-000000000703",
        "type": "categories",
        "title": "Browse by kind",
        "layout": "row",
        "items": [
          { "key": "on_the_boat", "label": "On the boat", "icon": "sailing", "tone": "primary" },
          { "key": "birds", "label": "Birds", "icon": "flutter_dash", "tone": "secondary" }
        ]
      },
      {
        "id": "0199c3a0-5eed-7000-8000-000000000704",
        "type": "trips",
        "title": "On the water",
        "layout": "row",
        "items": [
          { "slug": "reed-kayak", "title": "Reed-cutters' channels by kayak", "durationLabel": "05:00 → 21:00", "price": { "amount": 520, "currency": "EGP" } },
          { "title": "No slug", "durationLabel": "", "price": { "amount": 1, "currency": "EGP" } }
        ]
      },
      {
        "type": "trips",
        "title": "No id",
        "layout": "row",
        "items": [ { "slug": "a", "title": "A", "durationLabel": "", "price": { "amount": 1, "currency": "EGP" } } ]
      }
    ]
  }
}
"""

    /**
     * The M4-B4 additions in the contract's shape, as the seed serves them in Arabic (`seed-home.sql`):
     * a hero slide with every hero field, one with none (an older server's), one whose `url` action this
     * build does not open yet but which asks for the forward button, the trailing `note` with its
     * `directions_bus` icon, a titled note with an icon this build does not have, a note whose
     * blank body is unreadable, and a note with no `icon` key.
     */
    const val HERO_AND_NOTES = """
{
  "success": true,
  "data": {
    "sections": [
      {
        "id": "0199c3a0-5eed-7000-8000-000000000701",
        "type": "banners",
        "layout": "carousel",
        "aspectRatio": "45:28",
        "items": [
          {
            "id": "0199c3a0-5eed-7000-8000-000000000801",
            "title": "الفجر على بحيرة البرلس",
            "image": { "url": "https://cdn.bahr.eg/seed/joy-boats.png", "width": 900, "height": 560 },
            "action": { "type": "trip", "value": "burullus-dawn" },
            "badge": { "label": "الأكثر حجزًا", "tone": "secondary" },
            "price": { "amount": 450, "currency": "EGP" },
            "meta": "بحيرة البرلس · يوم واحد",
            "showForward": true
          },
          {
            "id": "0199c3a0-5eed-7000-8000-000000000802",
            "title": "موسم الفلامنجو",
            "image": { "url": "https://cdn.bahr.eg/seed/joy-birds.png", "width": 900, "height": 560 },
            "action": { "type": "category", "value": "birds" }
          },
          {
            "id": "0199c3a0-5eed-7000-8000-000000000804",
            "image": { "url": "https://cdn.bahr.eg/seed/joy-market.png", "width": 900, "height": 560 },
            "action": { "type": "url", "value": "https://bahr.eg/about" },
            "showForward": true
          }
        ]
      },
      {
        "id": "0199c3a0-5eed-7000-8000-000000000706",
        "type": "note",
        "layout": "row",
        "items": [
          { "body": "كل الرحلات بتتحرك من موقف عبد المنعم رياض في القاهرة، وميعاد الأتوبيس مكتوب في صفحة كل رحلة.", "icon": "directions_bus" }
        ]
      },
      {
        "id": "0199c3a0-5eed-7000-8000-000000000707",
        "type": "note",
        "title": "قبل ما تحجز",
        "layout": "row",
        "items": [ { "body": "الأسعار بالجنيه المصري.", "icon": "a_symbol_this_build_lacks" } ]
      },
      {
        "id": "0199c3a0-5eed-7000-8000-000000000708",
        "type": "note",
        "layout": "row",
        "items": [ { "body": "  " } ]
      },
      {
        "id": "0199c3a0-5eed-7000-8000-000000000709",
        "type": "note",
        "layout": "row",
        "items": [ { "body": "Bring a hat." } ]
      }
    ]
  }
}
"""

    /**
     * MH-B1/MH-B2 in the contract's shape: the new card types (`hosts` as a rail, `departures` as a grid with
     * one card whose date does not parse, `reviews` as a list), a tile grid asking for 5 columns, and what this
     * build must drop: a hosts section laid out as a hero (shape mismatch) and banners in a layout it does not know.
     */
    const val CARD_SECTIONS = """
{
  "success": true,
  "data": {
    "sections": [
      {
        "id": "0199c3a0-5eed-7000-8000-000000000710",
        "type": "hosts",
        "title": "Featured hosts",
        "layout": "card_rail",
        "items": [
          { "slug": "ashraf-el-bahr", "kind": "person", "name": "Ashraf El-Bahr",
            "avatar": { "url": "https://cdn.invalid/ashraf.jpg", "width": 400, "height": 400 },
            "verified": true, "tripsRun": 42, "rating": { "value": 4.8, "count": 37 } },
          { "slug": "burullus-boats", "kind": "company", "name": "Burullus Boats", "verified": false, "tripsRun": 0 }
        ]
      },
      {
        "id": "0199c3a0-5eed-7000-8000-000000000711",
        "type": "departures",
        "title": "Leaving soon",
        "layout": "card_grid",
        "columns": 2,
        "items": [
          { "id": "0199c3a0-5eed-7000-8000-000000000801", "date": "2026-11-14", "departTime": "05:00", "seatsRemaining": 3,
            "price": { "amount": 450, "currency": "EGP" },
            "trip": { "slug": "burullus-dawn", "title": "Dawn on Lake Burullus",
              "image": { "url": "https://cdn.invalid/dawn.jpg", "width": 1200, "height": 750 } } },
          { "id": "0199c3a0-5eed-7000-8000-000000000802", "date": "14 Nov", "departTime": "05:00", "seatsRemaining": 3,
            "price": { "amount": 450, "currency": "EGP" }, "trip": { "slug": "x", "title": "X" } }
        ]
      },
      {
        "id": "0199c3a0-5eed-7000-8000-000000000712",
        "type": "reviews",
        "title": "What travellers say",
        "layout": "card_list",
        "items": [
          { "id": "0199c3a0-5eed-7000-8000-000000000901", "rating": 5, "body": "The flamingos at sunrise were worth the 4 am start.",
            "author": "Mona S.", "tone": "secondary", "dateISO": "2026-10-20", "verified": true,
            "trip": { "slug": "burullus-dawn", "title": "Dawn on Lake Burullus" } }
        ]
      },
      {
        "id": "0199c3a0-5eed-7000-8000-000000000713",
        "type": "banners",
        "layout": "tile_grid",
        "columns": 5,
        "aspectRatio": "1:1",
        "items": [
          { "id": "0199c3a0-5eed-7000-8000-000000000a01", "image": { "url": "https://cdn.invalid/t.jpg", "width": 600, "height": 600 },
            "action": { "type": "none" } }
        ]
      },
      {
        "id": "0199c3a0-5eed-7000-8000-000000000714",
        "type": "hosts",
        "layout": "hero",
        "items": [ { "slug": "ashraf-el-bahr", "kind": "person", "name": "Ashraf", "verified": true, "tripsRun": 1 } ]
      },
      {
        "id": "0199c3a0-5eed-7000-8000-000000000715",
        "type": "banners",
        "layout": "mosaic",
        "items": [
          { "id": "0199c3a0-5eed-7000-8000-000000000a02", "image": { "url": "https://cdn.invalid/t.jpg", "width": 600, "height": 600 },
            "action": { "type": "none" } }
        ]
      }
    ]
  }
}
"""
}
