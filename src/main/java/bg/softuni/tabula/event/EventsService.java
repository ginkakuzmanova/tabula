package bg.softuni.tabula.event;

import static java.time.temporal.TemporalAdjusters.firstDayOfMonth;

import bg.softuni.tabula.event.dto.CalendarDayDTO;
import bg.softuni.tabula.event.dto.EventDTO;
import bg.softuni.tabula.event.dto.EventMapper;
import bg.softuni.tabula.event.dto.CalendarWeekDTO;
import bg.softuni.tabula.event.model.EventEntity;
import bg.softuni.tabula.event.model.EventType;
import bg.softuni.tabula.event.repository.EventRepository;
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

    Map<Integer, List<EventDTO>> currentEvents = extractEvents(monthInYear);

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

  private Map<Integer, List<EventDTO>> extractEvents(YearMonth monthInYear) {
    // TODO: optimize the query to filter out most events.
    List<EventEntity> relevantEvents =
        eventRepository.findAll();

    Map<Integer, List<EventDTO>> result = relevantEvents.
        stream().
        filter(e -> isRelevant(e, monthInYear)).
        map(EventMapper.INSTANCE::mapEntityToDto).
        flatMap(eventDTO -> multiply(eventDTO).stream()).
        //TODO - adjust event times
        collect(Collectors.groupingBy(eventDTO -> eventDTO.getEventTime().getDayOfMonth()));

    return result;
  }

  private List<EventDTO> multiply(EventDTO eventDTO) {
    if (eventDTO.getEventType() == EventType.WEEKLY) {
      // weekly events should be multiplied for each week.
      List<EventDTO> result = new LinkedList<>();
      EventDTO nextEventDTO = eventDTO;
      do {
        // FIXME - this algorithm is applicable for the current month,
        // please rework
        result.add(nextEventDTO);

        LocalDateTime nextEventTime = nextEventDTO.getEventTime();
        nextEventTime = nextEventTime.plusWeeks(1);
        if (nextEventTime.getMonth() == eventDTO.getEventTime().getMonth()) {
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

  private boolean isRelevant(EventEntity event, YearMonth monthInYear) {

    LocalDateTime occurrence = asLocal(event.getOccurrence());
    LocalDateTime startOfShownMonth = monthInYear.atDay(1).atStartOfDay();
    LocalDateTime endOfShownMonth = monthInYear.atEndOfMonth().atStartOfDay().plusDays(1);

    switch (event.getEventType()) {
      case ANNUALLY:
        return startOfShownMonth.isBefore(occurrence) &&
            occurrence.getMonth() == startOfShownMonth.getMonth();
      case MONTHLY:
      case WEEKLY:
        return occurrence.isBefore(endOfShownMonth);
      case SINGLE:
        return monthInYear.getYear() == occurrence.getYear() &&
            monthInYear.getMonth() == occurrence.getMonth();
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
