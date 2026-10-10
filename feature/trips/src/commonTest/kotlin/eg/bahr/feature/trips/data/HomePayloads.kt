package eg.bahr.feature.trips.data

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
}
