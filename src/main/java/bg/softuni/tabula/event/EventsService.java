package bg.softuni.tabula.event;

import static java.time.temporal.TemporalAdjusters.nextOrSame;
import bg.softuni.tabula.event.dto.CalendarDayDTO;
import bg.softuni.tabula.event.dto.EventDTO;
import bg.softuni.tabula.event.dto.EventMapper;
import bg.softuni.tabula.event.dto.CalendarWeekDTO;
import bg.softuni.tabula.event.model.EventEntity;
import bg.softuni.tabula.event.model.EventType;
import bg.softuni.tabula.event.repository.EventRepository;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EventsService {

  private static final Logger LOGGER = LoggerFactory.getLogger(EventsService.class);

  private final EventRepository eventRepository;

  public EventsService(EventRepository eventRepository) {

    this.eventRepository = eventRepository;
  }

  public void updateOrCreateEvent(EventDTO eventDTO){

    LOGGER.debug("Creating or updating an event.");

    EventEntity eventEntity = EventMapper.INSTANCE.mapDtoToEntity(eventDTO);
    eventRepository.save(eventEntity);
  }

  public List<CalendarWeekDTO> getEventsForMonth(YearMonth monthInYear) {

    Map<Integer, List<EventDTO>> currentEvents = extractCurrentEvents(monthInYear);

    List<CalendarWeekDTO> result = new ArrayList<>();
    CalendarWeekDTO currentWeek = new CalendarWeekDTO();

    int daysInMonth = getDaysInMonth(monthInYear);
    int dayInWeek = getFirstDayInWeek(monthInYear);

    // fill with empty cells at the start of the month
    for (int currentWeekDay = 0; currentWeekDay < dayInWeek; currentWeekDay++) {
      currentWeek.addDay(CalendarDayDTO.ofEmpty());
    }

    // fill in days
    for (int day = 1; day <= daysInMonth; day++) {

      CalendarDayDTO dayDTO = CalendarDayDTO.ofDay(day);

      // fill in the events
      List<EventDTO> daysEvents = currentEvents.getOrDefault(dayDTO.getDay(), Collections.emptyList());
      dayDTO.setEvents(daysEvents);
      currentWeek.addDay(dayDTO);
      //

      dayInWeek = (++dayInWeek) % 7;

      if (dayInWeek == 0) {
        result.add(currentWeek);
        currentWeek = new CalendarWeekDTO();
      }
    }

    // fill in empty days at the end of the calendar
    if (dayInWeek > 0) {
      for (int weekDay = dayInWeek; weekDay < 7; weekDay++) {
        currentWeek.addDay(CalendarDayDTO.ofEmpty());
      }
      result.add(currentWeek);
    }

    return result;
  }

  private Map<Integer, List<EventDTO>> extractCurrentEvents(YearMonth requestedMonth) {
    // TODO: optimize the query to filter out most events.
    List<EventEntity> relevantEvents =
        eventRepository.findAll();

    return relevantEvents.
        stream().
        filter(e -> isRelevant(e, requestedMonth)).
        map(EventMapper.INSTANCE::mapEntityToDto).
        flatMap(eventDTO -> multiply(eventDTO, requestedMonth).stream()).
        map(eventDTO -> adjustEventDate(eventDTO, requestedMonth)).
        collect(Collectors.groupingBy(eventDTO -> eventDTO.getEventTime().getDayOfMonth()));
  }

  private EventDTO adjustEventDate(EventDTO event, YearMonth requestedMonth) {
    LocalDateTime adjustedDateTime = event.getEventTime().
        withYear(requestedMonth.getYear()).
        withMonth(requestedMonth.getMonth().getValue());
    event.setEventTime(adjustedDateTime);
    return event;
  }

  private List<EventDTO> multiply(EventDTO eventDTO, YearMonth requestedMonth) {
    if (eventDTO.getEventType() == EventType.WEEKLY) {
      // weekly events should be multiplied for each week of the month.
      List<EventDTO> result = new LinkedList<>();
      EventDTO nextEventDTO = getFirstEvent(eventDTO, requestedMonth);
      do {
        result.add(nextEventDTO);

        LocalDateTime nextEventTime = nextEventDTO.getEventTime();
        nextEventTime = nextEventTime.plusWeeks(1);
        if (nextEventTime.getMonth() == requestedMonth.getMonth()) {
          nextEventDTO = EventMapper.INSTANCE.copy(nextEventDTO);
          nextEventDTO.setEventTime(nextEventTime);
        } else {
          nextEventDTO = null;
        }
      } while(nextEventDTO != null);
      return result;
    } else {
      return Collections.singletonList(eventDTO);
    }
  }

  private EventDTO getFirstEvent(EventDTO eventDTO, YearMonth monthInYear) {
    EventDTO result;
    LocalDateTime eventTime = eventDTO.getEventTime();
    if (eventTime.getMonth() == monthInYear.getMonth() &&
        eventTime.getYear() == monthInYear.getYear()) {
      // this is for the current month and year, should get in
      result = eventDTO;
    } else {
      // move to the first day of week in the month
      result = EventMapper.INSTANCE.copy(eventDTO);
      DayOfWeek eventDayOfWeek = eventTime.getDayOfWeek();
      LocalDateTime actualEventTime =
          eventTime.
              withMonth(monthInYear.getMonth().getValue()).
              withYear(monthInYear.getYear()).
              withDayOfMonth(1).
              with(nextOrSame(eventDayOfWeek));
      result.setEventTime(actualEventTime);
    }
    return result;
  }

  /**
   * Filters the relevant events for the requested month. E.g. if the events are in
   * the past they will be filtered out. E.g. single event in the past. Also annual event in
   * the future.
   *
   * @param event
   * @param requestedMonth
   * @return
   */
  private boolean isRelevant(EventEntity event, YearMonth requestedMonth) {

    LocalDateTime occurrence = asLocal(event.getOccurrence());
    LocalDateTime startOfShownMonth = requestedMonth.atDay(1).atStartOfDay();
    LocalDateTime endOfShownMonth = requestedMonth.atEndOfMonth().atStartOfDay().plusDays(1);

    switch (event.getEventType()) {
      case ANNUALLY:
        return startOfShownMonth.isBefore(occurrence) &&
            occurrence.getMonth() == startOfShownMonth.getMonth();
      case MONTHLY:
      case WEEKLY:
        return occurrence.isBefore(endOfShownMonth);
      case SINGLE:
        return requestedMonth.getYear() == occurrence.getYear() &&
            requestedMonth.getMonth() == occurrence.getMonth();
      default:
        return false;
    }
  }

  private LocalDateTime asLocal(Instant instant) {

    return instant.
        atZone(ZoneId.systemDefault()).
        toLocalDateTime();
  }

  private int getDaysInMonth(YearMonth monthInYear) {
    return (int)ChronoUnit.DAYS.between(
        monthInYear.atDay(1),
        monthInYear.atDay(1).plusMonths(1));
  }

  private int getFirstDayInWeek(YearMonth monthInYear) {
    return monthInYear.
        atDay(1).
        getDayOfWeek().
        getValue() - 1;
  }

}
