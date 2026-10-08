package eg.bahr.core.designsystem.icon

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import eg.bahr.core.designsystem.generated.resources.Res
import eg.bahr.core.designsystem.generated.resources.ic_add
import eg.bahr.core.designsystem.generated.resources.ic_apps
import eg.bahr.core.designsystem.generated.resources.ic_arrow_back
import eg.bahr.core.designsystem.generated.resources.ic_arrow_forward
import eg.bahr.core.designsystem.generated.resources.ic_backpack
import eg.bahr.core.designsystem.generated.resources.ic_backpack_filled
import eg.bahr.core.designsystem.generated.resources.ic_beach_access
import eg.bahr.core.designsystem.generated.resources.ic_beach_access_filled
import eg.bahr.core.designsystem.generated.resources.ic_bedtime
import eg.bahr.core.designsystem.generated.resources.ic_bedtime_filled
import eg.bahr.core.designsystem.generated.resources.ic_block
import eg.bahr.core.designsystem.generated.resources.ic_calendar_month
import eg.bahr.core.designsystem.generated.resources.ic_calendar_month_filled
import eg.bahr.core.designsystem.generated.resources.ic_campaign
import eg.bahr.core.designsystem.generated.resources.ic_campaign_filled
import eg.bahr.core.designsystem.generated.resources.ic_chat
import eg.bahr.core.designsystem.generated.resources.ic_chat_filled
import eg.bahr.core.designsystem.generated.resources.ic_check
import eg.bahr.core.designsystem.generated.resources.ic_circle
import eg.bahr.core.designsystem.generated.resources.ic_circle_filled
import eg.bahr.core.designsystem.generated.resources.ic_close
import eg.bahr.core.designsystem.generated.resources.ic_credit_card
import eg.bahr.core.designsystem.generated.resources.ic_credit_card_filled
import eg.bahr.core.designsystem.generated.resources.ic_directions_bus
import eg.bahr.core.designsystem.generated.resources.ic_directions_bus_filled
import eg.bahr.core.designsystem.generated.resources.ic_event
import eg.bahr.core.designsystem.generated.resources.ic_event_busy
import eg.bahr.core.designsystem.generated.resources.ic_event_busy_filled
import eg.bahr.core.designsystem.generated.resources.ic_event_filled
import eg.bahr.core.designsystem.generated.resources.ic_event_seat
import eg.bahr.core.designsystem.generated.resources.ic_event_seat_filled
import eg.bahr.core.designsystem.generated.resources.ic_event_upcoming
import eg.bahr.core.designsystem.generated.resources.ic_event_upcoming_filled
import eg.bahr.core.designsystem.generated.resources.ic_favorite
import eg.bahr.core.designsystem.generated.resources.ic_favorite_filled
import eg.bahr.core.designsystem.generated.resources.ic_flutter_dash
import eg.bahr.core.designsystem.generated.resources.ic_format_list_bulleted
import eg.bahr.core.designsystem.generated.resources.ic_groups
import eg.bahr.core.designsystem.generated.resources.ic_groups_filled
import eg.bahr.core.designsystem.generated.resources.ic_home
import eg.bahr.core.designsystem.generated.resources.ic_home_filled
import eg.bahr.core.designsystem.generated.resources.ic_info
import eg.bahr.core.designsystem.generated.resources.ic_info_filled
import eg.bahr.core.designsystem.generated.resources.ic_ios_share
import eg.bahr.core.designsystem.generated.resources.ic_local_cafe
import eg.bahr.core.designsystem.generated.resources.ic_local_cafe_filled
import eg.bahr.core.designsystem.generated.resources.ic_location_on
import eg.bahr.core.designsystem.generated.resources.ic_location_on_filled
import eg.bahr.core.designsystem.generated.resources.ic_lock
import eg.bahr.core.designsystem.generated.resources.ic_lock_filled
import eg.bahr.core.designsystem.generated.resources.ic_map
import eg.bahr.core.designsystem.generated.resources.ic_map_filled
import eg.bahr.core.designsystem.generated.resources.ic_more_vert
import eg.bahr.core.designsystem.generated.resources.ic_notifications_active
import eg.bahr.core.designsystem.generated.resources.ic_notifications_active_filled
import eg.bahr.core.designsystem.generated.resources.ic_palette
import eg.bahr.core.designsystem.generated.resources.ic_palette_filled
import eg.bahr.core.designsystem.generated.resources.ic_payments
import eg.bahr.core.designsystem.generated.resources.ic_payments_filled
import eg.bahr.core.designsystem.generated.resources.ic_person
import eg.bahr.core.designsystem.generated.resources.ic_person_filled
import eg.bahr.core.designsystem.generated.resources.ic_pin_drop
import eg.bahr.core.designsystem.generated.resources.ic_pin_drop_filled
import eg.bahr.core.designsystem.generated.resources.ic_remove
import eg.bahr.core.designsystem.generated.resources.ic_restaurant
import eg.bahr.core.designsystem.generated.resources.ic_sailing
import eg.bahr.core.designsystem.generated.resources.ic_sailing_filled
import eg.bahr.core.designsystem.generated.resources.ic_schedule
import eg.bahr.core.designsystem.generated.resources.ic_schedule_filled
import eg.bahr.core.designsystem.generated.resources.ic_sell
import eg.bahr.core.designsystem.generated.resources.ic_sell_filled
import eg.bahr.core.designsystem.generated.resources.ic_smartphone
import eg.bahr.core.designsystem.generated.resources.ic_smartphone_filled
import eg.bahr.core.designsystem.generated.resources.ic_star
import eg.bahr.core.designsystem.generated.resources.ic_star_filled
import eg.bahr.core.designsystem.generated.resources.ic_storefront
import eg.bahr.core.designsystem.generated.resources.ic_storefront_filled
import eg.bahr.core.designsystem.generated.resources.ic_sunny
import eg.bahr.core.designsystem.generated.resources.ic_sunny_filled
import eg.bahr.core.designsystem.generated.resources.ic_timer
import eg.bahr.core.designsystem.generated.resources.ic_timer_filled
import eg.bahr.core.designsystem.generated.resources.ic_verified
import eg.bahr.core.designsystem.generated.resources.ic_verified_filled
import eg.bahr.core.designsystem.generated.resources.ic_visibility
import eg.bahr.core.designsystem.generated.resources.ic_visibility_filled
import eg.bahr.core.designsystem.generated.resources.ic_waves
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.vectorResource

/**
 * Every icon the design uses: Material Symbols Rounded, 24px, weight 400, grade 0, the axes the
 * prototype sets on its icon font. Features use these entries and never `Res.drawable.*`. The
 * resource class is internal to this module, so the compiler enforces that.
 *
 * Each entry has an [outlined] (FILL 0) and a [filled] (FILL 1) form. The prototype draws an icon
 * filled where its markup says `class="ms f"`. Where the two forms are identical upstream (arrows,
 * add, close, …), both return the same drawable.
 *
 * **Direction.** Mirroring is built into the drawables (`android:autoMirrored`, which
 * `vectorResource` honours), exactly where Google marks the symbol as directional: [ArrowBack],
 * [ArrowForward], [Chat], [EventUpcoming], [FormatListBulleted]. In RTL they flip on their own.
 * Always use [ArrowBack] for "back" and [ArrowForward] for a forward CTA, and never pick an icon by
 * locale. The prototype swaps `arrow_back`/`arrow_forward` by language only because its icon font
 * cannot mirror; doing that here as well would flip the arrow twice.
 *
 * To add one: put the Symbols name in `scripts/import-material-symbols.sh`, run it, add the entry.
 */
enum class BahrIcons(
    private val outlinedRes: DrawableResource,
    private val filledRes: DrawableResource,
) {
    Add(Res.drawable.ic_add, Res.drawable.ic_add),
    Apps(Res.drawable.ic_apps, Res.drawable.ic_apps),
    ArrowBack(Res.drawable.ic_arrow_back, Res.drawable.ic_arrow_back),
    ArrowForward(Res.drawable.ic_arrow_forward, Res.drawable.ic_arrow_forward),
    Backpack(Res.drawable.ic_backpack, Res.drawable.ic_backpack_filled),
    BeachAccess(Res.drawable.ic_beach_access, Res.drawable.ic_beach_access_filled),
    Bedtime(Res.drawable.ic_bedtime, Res.drawable.ic_bedtime_filled),
    Block(Res.drawable.ic_block, Res.drawable.ic_block),
    CalendarMonth(Res.drawable.ic_calendar_month, Res.drawable.ic_calendar_month_filled),
    Campaign(Res.drawable.ic_campaign, Res.drawable.ic_campaign_filled),
    Chat(Res.drawable.ic_chat, Res.drawable.ic_chat_filled),
    Check(Res.drawable.ic_check, Res.drawable.ic_check),
    Circle(Res.drawable.ic_circle, Res.drawable.ic_circle_filled),
    Close(Res.drawable.ic_close, Res.drawable.ic_close),
    CreditCard(Res.drawable.ic_credit_card, Res.drawable.ic_credit_card_filled),
    DirectionsBus(Res.drawable.ic_directions_bus, Res.drawable.ic_directions_bus_filled),
    Event(Res.drawable.ic_event, Res.drawable.ic_event_filled),
    EventBusy(Res.drawable.ic_event_busy, Res.drawable.ic_event_busy_filled),
    EventSeat(Res.drawable.ic_event_seat, Res.drawable.ic_event_seat_filled),
    EventUpcoming(Res.drawable.ic_event_upcoming, Res.drawable.ic_event_upcoming_filled),
    Favorite(Res.drawable.ic_favorite, Res.drawable.ic_favorite_filled),
    FlutterDash(Res.drawable.ic_flutter_dash, Res.drawable.ic_flutter_dash),
    FormatListBulleted(Res.drawable.ic_format_list_bulleted, Res.drawable.ic_format_list_bulleted),
    Groups(Res.drawable.ic_groups, Res.drawable.ic_groups_filled),
    Home(Res.drawable.ic_home, Res.drawable.ic_home_filled),
    Info(Res.drawable.ic_info, Res.drawable.ic_info_filled),
    IosShare(Res.drawable.ic_ios_share, Res.drawable.ic_ios_share),
    LocalCafe(Res.drawable.ic_local_cafe, Res.drawable.ic_local_cafe_filled),

    /** The prototype's `place`: a ligature alias in the icon font for the `location_on` symbol. */
    Place(Res.drawable.ic_location_on, Res.drawable.ic_location_on_filled),
    Lock(Res.drawable.ic_lock, Res.drawable.ic_lock_filled),
    Map(Res.drawable.ic_map, Res.drawable.ic_map_filled),
    MoreVert(Res.drawable.ic_more_vert, Res.drawable.ic_more_vert),
    NotificationsActive(Res.drawable.ic_notifications_active, Res.drawable.ic_notifications_active_filled),
    Palette(Res.drawable.ic_palette, Res.drawable.ic_palette_filled),
    Payments(Res.drawable.ic_payments, Res.drawable.ic_payments_filled),
    Person(Res.drawable.ic_person, Res.drawable.ic_person_filled),
    PinDrop(Res.drawable.ic_pin_drop, Res.drawable.ic_pin_drop_filled),
    Remove(Res.drawable.ic_remove, Res.drawable.ic_remove),
    Restaurant(Res.drawable.ic_restaurant, Res.drawable.ic_restaurant),
    Sailing(Res.drawable.ic_sailing, Res.drawable.ic_sailing_filled),
    Schedule(Res.drawable.ic_schedule, Res.drawable.ic_schedule_filled),
    Sell(Res.drawable.ic_sell, Res.drawable.ic_sell_filled),
    Smartphone(Res.drawable.ic_smartphone, Res.drawable.ic_smartphone_filled),
    Star(Res.drawable.ic_star, Res.drawable.ic_star_filled),
    Storefront(Res.drawable.ic_storefront, Res.drawable.ic_storefront_filled),
    Sunny(Res.drawable.ic_sunny, Res.drawable.ic_sunny_filled),
    Timer(Res.drawable.ic_timer, Res.drawable.ic_timer_filled),
    Verified(Res.drawable.ic_verified, Res.drawable.ic_verified_filled),
    Visibility(Res.drawable.ic_visibility, Res.drawable.ic_visibility_filled),
    Waves(Res.drawable.ic_waves, Res.drawable.ic_waves),
    ;

    /** FILL 0, the prototype's default (`class="ms"`). */
    @Composable
    fun outlined(): ImageVector = vectorResource(outlinedRes)

    /** FILL 1 (`class="ms f"`). */
    @Composable
    fun filled(): ImageVector = vectorResource(filledRes)
}
