package eg.bahr.feature.trips.data

/**
 * `GET /api/v1/hosts/{slug}` (`getHost`, M4-B7) in the contract's `HostProfile` shape, from the seed
 * (bahr-be `scripts/seed-burullus.sql`, `seed-home.sql`): Ashraf El Bahr (a person, with reviews) and
 * Red Sea Days (a company, no reviews yet: `count` 0, no `average`, five zero buckets). Null fields are
 * omitted, as the backend omits them. No phone or WhatsApp link: the contract never serves them here.
 * Since M4-B10 the name is in the request's language, so the Arabic copy carries the Arabic name.
 */
internal object HostPayloads {
    val ashrafEnglish =
        """
{
 "success": true,
 "data": {
  "slug": "ashraf-el-bahr",
  "kind": "person",
  "name": "Ashraf El Bahr",
  "avatar": { "url": "http://localhost:9000/bahr-assets/seed/host.png", "width": 240, "height": 240, "variants": [] },
  "role": "Boat owner, Burg El Burullus",
  "bio": "Third-generation fisherman. Takes guests out at dawn when the lake is still.",
  "verified": true,
  "memberSince": "2024-09-14",
  "tripsRun": 11,
  "rating": {
   "average": 4.7,
   "count": 6,
   "distribution": [
    { "stars": 5, "count": 4 },
    { "stars": 4, "count": 2 },
    { "stars": 3, "count": 0 },
    { "stars": 2, "count": 0 },
    { "stars": 1, "count": 0 }
   ]
  },
  "reviews": [
   { "author": "Mahmoud A.", "dateISO": "2026-09-21", "body": "The flamingos at sunrise were unreal.", "partyLabel": "Party of 2", "tone": "primary" },
   { "author": "Salma K.", "dateISO": "2026-09-07", "body": "Ashraf knows every channel of the lake.", "tone": "secondary" }
  ]
 },
 "error": null
}
        """.trimIndent()

    val redSeaDaysArabic =
        """
{
 "success": true,
 "data": {
  "slug": "red-sea-days",
  "kind": "company",
  "name": "ريد سي دايز",
  "avatar": { "url": "http://localhost:9000/bahr-assets/seed/host.png", "width": 240, "height": 240, "variants": [] },
  "role": "شركة رحلات يوم واحد، البحر الأحمر",
  "bio": "فريق صغير بينظم رحلات يوم واحد من القاهرة للعين السخنة ورأس سدر.",
  "verified": false,
  "memberSince": "2026-10-01",
  "tripsRun": 0,
  "rating": {
   "count": 0,
   "distribution": [
    { "stars": 5, "count": 0 },
    { "stars": 4, "count": 0 },
    { "stars": 3, "count": 0 },
    { "stars": 2, "count": 0 },
    { "stars": 1, "count": 0 }
   ]
  },
  "reviews": []
 },
 "error": null
}
        """.trimIndent()

    /** A host who is unknown or not approved (pending, suspended, rejected). */
    const val NOT_FOUND = """{"success":false,"data":null,"error":{"code":"NOT_FOUND","message":"Host not found"}}"""
}
