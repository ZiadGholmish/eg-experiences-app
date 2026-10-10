package eg.bahr.feature.map.data

/**
 * `GET /api/v1/trips/map` bodies captured from the local api (2026-10-10, seed data) with four of the
 * fifteen pins kept: a beach pin (`success`), two Burullus pins and the sold-out two-day White
 * Desert trip. Every pin carries every optional field, as the seed does. The pins leave Cairo at 05:00
 * and 06:00, so the departure marker reads the place alone.
 */
internal object MapPayloads {
    val ENGLISH =
        """
        {
          "success": true,
          "data": {
            "pins": [
              {
                "slug": "ain-sokhna-beach-day",
                "title": "A Red Sea beach day at Ain Sokhna",
                "subtitle": "Ain Sokhna · Suez",
                "price": {
                  "amount": 550,
                  "currency": "EGP"
                },
                "durationLabel": "06:00 → 21:00",
                "nights": 0,
                "lat": 29.6,
                "lng": 32.34,
                "placeName": "Ain Sokhna beach",
                "category": "beach",
                "tone": "success",
                "nextDeparture": {
                  "id": "0199c3a0-5eed-7000-8000-000000000645",
                  "date": "2026-10-16",
                  "returnDate": "2026-10-16",
                  "seatsRemaining": 6,
                  "capacity": 24,
                  "soldOut": false
                },
                "departTime": "06:00",
                "departurePointId": "0199c3a0-5eed-7000-8000-000000000201",
                "distanceKm": 130
              },
              {
                "slug": "boughaz-fish-market",
                "title": "The boughaz and the morning fish market",
                "subtitle": "Burg El Burullus · Kafr El Sheikh",
                "price": {
                  "amount": 380,
                  "currency": "EGP"
                },
                "durationLabel": "05:00 → 17:00",
                "nights": 0,
                "lat": 31.586,
                "lng": 30.981,
                "placeName": "The boughaz and the fish market",
                "category": "on_the_boat",
                "tone": "primary",
                "nextDeparture": {
                  "id": "0199c3a0-5eed-7000-8000-000000000613",
                  "date": "2026-10-17",
                  "returnDate": "2026-10-17",
                  "seatsRemaining": 14,
                  "capacity": 18,
                  "soldOut": false
                },
                "departTime": "05:00",
                "departurePointId": "0199c3a0-5eed-7000-8000-000000000201",
                "distanceKm": 150
              },
              {
                "slug": "burullus-dawn",
                "title": "Dawn on Lake Burullus",
                "subtitle": "Lake Burullus · Kafr El Sheikh",
                "price": {
                  "amount": 450,
                  "currency": "EGP"
                },
                "durationLabel": "05:00 → 22:00",
                "nights": 0,
                "lat": 31.503,
                "lng": 30.804,
                "placeName": "Burg El Burullus",
                "category": "on_the_boat",
                "tone": "primary",
                "nextDeparture": {
                  "id": "0199c3a0-5eed-7000-8000-000000000601",
                  "date": "2026-10-17",
                  "returnDate": "2026-10-17",
                  "seatsRemaining": 6,
                  "capacity": 18,
                  "soldOut": false
                },
                "departTime": "05:00",
                "departurePointId": "0199c3a0-5eed-7000-8000-000000000201",
                "distanceKm": 150
              },
              {
                "slug": "white-desert-overnight",
                "title": "A night in the White Desert",
                "subtitle": "Farafra · New Valley",
                "price": {
                  "amount": 2900,
                  "currency": "EGP"
                },
                "durationLabel": "2 days · 1 night",
                "nights": 1,
                "lat": 27.36,
                "lng": 28.17,
                "placeName": "The White Desert",
                "category": "night_trips",
                "tone": "primary",
                "nextDeparture": {
                  "id": "0199c3a0-5eed-7000-8000-000000000629",
                  "date": "2026-10-15",
                  "returnDate": "2026-10-16",
                  "seatsRemaining": 0,
                  "capacity": 12,
                  "soldOut": true
                },
                "departTime": "06:00",
                "departurePointId": "0199c3a0-5eed-7000-8000-000000000201",
                "distanceKm": 500
              }
            ],
            "departurePoints": [
              {
                "id": "0199c3a0-5eed-7000-8000-000000000201",
                "placeName": "Abdel Moneim Riad",
                "city": "Cairo",
                "governorate": "Cairo",
                "lat": 30.0566,
                "lng": 31.2288
              }
            ],
            "legend": [
              {
                "key": "on_the_boat",
                "label": "On the boat",
                "icon": "sailing",
                "tone": "primary"
              },
              {
                "key": "birds",
                "label": "Birds",
                "icon": "flutter_dash",
                "tone": "secondary"
              },
              {
                "key": "murals",
                "label": "Murals",
                "icon": "palette",
                "tone": "quaternary"
              },
              {
                "key": "beach",
                "label": "Beach",
                "icon": "beach_access",
                "tone": "success"
              },
              {
                "key": "night_trips",
                "label": "Night trips",
                "icon": "bedtime",
                "tone": "primary"
              }
            ]
          }
        }
        """.trimIndent()

    val ARABIC =
        """
        {
          "success": true,
          "data": {
            "pins": [
              {
                "slug": "ain-sokhna-beach-day",
                "title": "يوم على البحر الأحمر في العين السخنة",
                "subtitle": "العين السخنة · السويس",
                "price": {
                  "amount": 550,
                  "currency": "EGP"
                },
                "durationLabel": "06:00 → 21:00",
                "nights": 0,
                "lat": 29.6,
                "lng": 32.34,
                "placeName": "شاطئ العين السخنة",
                "category": "beach",
                "tone": "success",
                "nextDeparture": {
                  "id": "0199c3a0-5eed-7000-8000-000000000645",
                  "date": "2026-10-16",
                  "returnDate": "2026-10-16",
                  "seatsRemaining": 6,
                  "capacity": 24,
                  "soldOut": false
                },
                "departTime": "06:00",
                "departurePointId": "0199c3a0-5eed-7000-8000-000000000201",
                "distanceKm": 130
              },
              {
                "slug": "boughaz-fish-market",
                "title": "البوغاز وسوق السمك الصبح",
                "subtitle": "برج البرلس · كفر الشيخ",
                "price": {
                  "amount": 380,
                  "currency": "EGP"
                },
                "durationLabel": "05:00 → 17:00",
                "nights": 0,
                "lat": 31.586,
                "lng": 30.981,
                "placeName": "البوغاز وسوق السمك",
                "category": "on_the_boat",
                "tone": "primary",
                "nextDeparture": {
                  "id": "0199c3a0-5eed-7000-8000-000000000613",
                  "date": "2026-10-17",
                  "returnDate": "2026-10-17",
                  "seatsRemaining": 14,
                  "capacity": 18,
                  "soldOut": false
                },
                "departTime": "05:00",
                "departurePointId": "0199c3a0-5eed-7000-8000-000000000201",
                "distanceKm": 150
              },
              {
                "slug": "burullus-dawn",
                "title": "الفجر على بحيرة البرلس",
                "subtitle": "بحيرة البرلس · كفر الشيخ",
                "price": {
                  "amount": 450,
                  "currency": "EGP"
                },
                "durationLabel": "05:00 → 22:00",
                "nights": 0,
                "lat": 31.503,
                "lng": 30.804,
                "placeName": "برج البرلس",
                "category": "on_the_boat",
                "tone": "primary",
                "nextDeparture": {
                  "id": "0199c3a0-5eed-7000-8000-000000000601",
                  "date": "2026-10-17",
                  "returnDate": "2026-10-17",
                  "seatsRemaining": 6,
                  "capacity": 18,
                  "soldOut": false
                },
                "departTime": "05:00",
                "departurePointId": "0199c3a0-5eed-7000-8000-000000000201",
                "distanceKm": 150
              },
              {
                "slug": "white-desert-overnight",
                "title": "ليلة في الصحراء البيضاء",
                "subtitle": "الفرافرة · الوادي الجديد",
                "price": {
                  "amount": 2900,
                  "currency": "EGP"
                },
                "durationLabel": "يومان · ليلة واحدة",
                "nights": 1,
                "lat": 27.36,
                "lng": 28.17,
                "placeName": "الصحراء البيضاء",
                "category": "night_trips",
                "tone": "primary",
                "nextDeparture": {
                  "id": "0199c3a0-5eed-7000-8000-000000000629",
                  "date": "2026-10-15",
                  "returnDate": "2026-10-16",
                  "seatsRemaining": 0,
                  "capacity": 12,
                  "soldOut": true
                },
                "departTime": "06:00",
                "departurePointId": "0199c3a0-5eed-7000-8000-000000000201",
                "distanceKm": 500
              }
            ],
            "departurePoints": [
              {
                "id": "0199c3a0-5eed-7000-8000-000000000201",
                "placeName": "موقف عبد المنعم رياض",
                "city": "القاهرة",
                "governorate": "القاهرة",
                "lat": 30.0566,
                "lng": 31.2288
              }
            ],
            "legend": [
              {
                "key": "on_the_boat",
                "label": "في القارب",
                "icon": "sailing",
                "tone": "primary"
              },
              {
                "key": "birds",
                "label": "طيور",
                "icon": "flutter_dash",
                "tone": "secondary"
              },
              {
                "key": "murals",
                "label": "جداريات",
                "icon": "palette",
                "tone": "quaternary"
              },
              {
                "key": "beach",
                "label": "شاطئ",
                "icon": "beach_access",
                "tone": "success"
              },
              {
                "key": "night_trips",
                "label": "رحلات ليلية",
                "icon": "bedtime",
                "tone": "primary"
              }
            ]
          }
        }
        """.trimIndent()

    /** A pin as an older server (before M4-B3/B3b) sends it: only the required fields, plus a field this build does not know. */
    val MINIMAL =
        """
        {"success":true,"data":{"pins":[{"slug":"burullus-dawn","title":"Dawn on Lake Burullus",
        "price":{"amount":450,"currency":"EGP"},"durationLabel":"05:00 → 22:00","nights":0,
        "lat":31.503,"lng":30.804,"tone":"primary","rating":4.8}],"departurePoints":[],"legend":[]}}
        """.trimIndent()

    val NOT_FOUND =
        """{"success":false,"error":{"code":"NOT_FOUND","message":"No such thing"}}"""
}
