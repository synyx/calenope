package de.synyx.calenope.organizer.ui

import android.graphics.Color
import android.support.design.widget.AppBarLayout
import android.support.design.widget.CoordinatorLayout
import android.widget.LinearLayout
import com.alamkanak.weekview.MonthLoader
import com.alamkanak.weekview.WeekView
import com.alamkanak.weekview.WeekViewEvent
import de.synyx.calenope.core.api.model.Event
import de.synyx.calenope.organizer.Action
import de.synyx.calenope.organizer.Application
import de.synyx.calenope.organizer.R
import de.synyx.calenope.organizer.component.donut
import org.joda.time.DateTime
import org.joda.time.Instant
import org.joda.time.Minutes
import rx.Observer
import trikita.anvil.Anvil
import trikita.anvil.BaseDSL.MATCH
import trikita.anvil.DSL
import trikita.anvil.DSL.WRAP
import trikita.anvil.DSL.init
import trikita.anvil.DSL.layoutParams
import trikita.anvil.DSL.orientation
import trikita.anvil.DSL.relativeLayout
import trikita.anvil.DSL.size
import trikita.anvil.DSL.v
import trikita.anvil.DSL.visibility
import trikita.anvil.RenderableView
import trikita.anvil.appcompat.v7.AppCompatv7DSL.popupTheme
import trikita.anvil.appcompat.v7.AppCompatv7DSL.toolbar
import trikita.anvil.design.DesignDSL.appBarLayout
import trikita.anvil.design.DesignDSL.coordinatorLayout
import trikita.jedux.Store
import java.util.*

/**
 * @author clausen - clausen@synyx.de
 */
class WeekviewLayout (private val weekview : Weekview) : RenderableView (weekview) {

    private val store by lazy { Application.store () }

    private val scrolling by lazy {
        val params = CoordinatorLayout.LayoutParams (MATCH, MATCH)
            params.behavior = AppBarLayout.ScrollingViewBehavior ()
            params
    }

    private lateinit var events : MonthLoaderAdapter<Event>

    override fun view () {
        weekview ()
        bind ()
    }

    private fun bind () {
        events.onNext (store.state.events.map)
    }

    private fun weekview () {
        coordinatorLayout {
            size (MATCH, MATCH)
            orientation (LinearLayout.VERTICAL)

            appBarLayout {
                size (MATCH, WRAP)

                toolbar {
                    init {
                        weekview.setTheme (R.style.AppTheme_AppBarOverlay)
                        weekview.setSupportActionBar (Anvil.currentView ())
                    }

                    popupTheme(R.style.AppTheme_PopupOverlay)
                }
            }

            relativeLayout {
                layoutParams (scrolling)

                size (MATCH, MATCH)

                donut {
                    visibility (store.state.events.synchronizing)
                }

                v (WeekView::class.java) {
                    init {
                        val week = Anvil.currentView<WeekView> ()

                        events = MonthLoaderAdapter<Event> (week, store)

                            week.monthChangeListener         = events
                            week.numberOfVisibleDays         = 1
                            week.columnGap                   = DSL.dip (8)
                            week.hourHeight                  = DSL.dip (600)
                            week.headerColumnPadding         = DSL.dip (8)
                            week.headerRowPadding            = DSL.dip (12)
                            week.textSize                    = DSL.sip (10)
                            week.eventTextSize               = DSL.sip (10)
                            week.eventTextColor              = Color.WHITE
                            week.headerColumnTextColor       = Color.parseColor ("#8f000000")
                            week.headerColumnBackgroundColor = Color.parseColor ("#ffffffff")
                            week.headerRowBackgroundColor    = Color.parseColor ("#ffefefef")
                            week.dayBackgroundColor          = Color.parseColor ("#05000000")
                            week.todayBackgroundColor        = Color.parseColor ("#1848adff")
                    }
                }
            }
        }
    }

    private class MonthLoaderAdapter<V : Event> (private val week : WeekView, private val store : Store<Action, *>) : MonthLoader.MonthChangeListener, Observer<Map<Pair<Int, Int>, Collection<V>>> {

        private val map : MutableMap<Pair<Int, Int>, Pair<Collection<V>, DateTime>> = mutableMapOf ()

        override fun onMonthChange     (year : Int, month : Int) : List<WeekViewEvent>? {
            val             key = Pair (year,       month)
            val value = map[key]

            if (value == null || outdates (value.second)) {
                store.dispatch (Action.SynchronizeCalendar (year, month))
            }

            return value?.first?.mapIndexed { index, event -> convert (index.toLong (), event) } ?: emptyList<WeekViewEvent> ()
        }

        override fun onNext (t : Map<Pair<Int, Int>, Collection<V>>) {
            val timestamp = DateTime.now ()
            map += t.mapValues { Pair (it.value, timestamp) }
            week.notifyDatasetChanged ()
        }

        override fun onCompleted () {
            week.notifyDatasetChanged ()
        }

        override fun onError (e : Throwable) {
            map.clear ()
            week.notifyDatasetChanged ()
        }

        private fun outdates       (target : DateTime,                  minutes : Minutes = Minutes.TWO) =
            Minutes.minutesBetween (target, DateTime ()).isGreaterThan (minutes)

        private fun convert(index : Long, event : Event) : WeekViewEvent {
            return WeekViewEvent (index, event.title (), event.location (), calendar (event.start ()), calendar (event.end ()))
        }

        private fun calendar (instant : Instant?) : Calendar {
            val calendar = Calendar.getInstance ()
                calendar.time = instant?.toDate ()

            return calendar
        }

    }

}
