package ma.elaroui.pos.desktop.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import ma.elaroui.pos.desktop.DesktopStrings

@Composable
fun TouchDatePickerModal(
    title: String,
    initialDate: LocalDate,
    minDate: LocalDate,
    maxDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    strings: DesktopStrings
) {
    val todayDate = remember { LocalDate.now() }
    val effectiveInitial = remember(initialDate, minDate, maxDate) {
        initialDate.coerceIn(minDate, maxDate)
    }
    var displayedMonth by remember { mutableStateOf(effectiveInitial.withDayOfMonth(1)) }
    var tempSelectedDate by remember { mutableStateOf(effectiveInitial) }

    val locale = Locale.FRANCE
    val monthTitle = remember(displayedMonth) {
        val monthName = displayedMonth.month.getDisplayName(TextStyle.FULL, locale)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
        monthName + " " + displayedMonth.year
    }

    val canGoPrevYear = remember(displayedMonth, minDate) {
        displayedMonth.minusYears(1).withDayOfMonth(displayedMonth.minusYears(1).lengthOfMonth()) >= minDate
    }
    val canGoPrevMonth = remember(displayedMonth, minDate) {
        displayedMonth.minusMonths(1).withDayOfMonth(displayedMonth.minusMonths(1).lengthOfMonth()) >= minDate
    }
    val canGoNextMonth = remember(displayedMonth, maxDate) {
        displayedMonth.plusMonths(1).withDayOfMonth(1) <= maxDate
    }
    val canGoNextYear = remember(displayedMonth, maxDate) {
        displayedMonth.plusYears(1).withDayOfMonth(1) <= maxDate
    }
    val canSelectToday = remember(todayDate, minDate, maxDate) {
        todayDate in minDate..maxDate
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = PosColors.TextHigh
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = PosColors.PrimaryLight
                ) {
                    Text(
                        tempSelectedDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = PosColors.Primary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(min = 340.dp, max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Month / Year Navigation Header
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = PosColors.Workspace,
                    border = BorderStroke(1.dp, PosColors.Border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            // Prev Year
                            IconButton(
                                onClick = {
                                    val target = displayedMonth.minusYears(1)
                                    displayedMonth = if (target.withDayOfMonth(target.lengthOfMonth()) < minDate) {
                                        minDate.withDayOfMonth(1)
                                    } else target
                                },
                                enabled = canGoPrevYear,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text("«", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            // Prev Month
                            IconButton(
                                onClick = {
                                    val target = displayedMonth.minusMonths(1)
                                    displayedMonth = if (target.withDayOfMonth(target.lengthOfMonth()) < minDate) {
                                        minDate.withDayOfMonth(1)
                                    } else target
                                },
                                enabled = canGoPrevMonth,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text("‹", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Text(
                            monthTitle,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = PosColors.TextHigh,
                            textAlign = TextAlign.Center
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            // Next Month
                            IconButton(
                                onClick = {
                                    val target = displayedMonth.plusMonths(1)
                                    displayedMonth = if (target.withDayOfMonth(1) > maxDate) {
                                        maxDate.withDayOfMonth(1)
                                    } else target
                                },
                                enabled = canGoNextMonth,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text("›", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                            // Next Year
                            IconButton(
                                onClick = {
                                    val target = displayedMonth.plusYears(1)
                                    displayedMonth = if (target.withDayOfMonth(1) > maxDate) {
                                        maxDate.withDayOfMonth(1)
                                    } else target
                                },
                                enabled = canGoNextYear,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Text("»", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Days of week header (Lun -> Dim)
                val dayHeaders = listOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    dayHeaders.forEach { d ->
                        Text(
                            d,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PosColors.TextMedium
                        )
                    }
                }

                HorizontalDivider(color = PosColors.Border, thickness = 0.8.dp)

                // Calendar Days Grid
                val daysInMonth = displayedMonth.lengthOfMonth()
                val firstDayOfWeek = displayedMonth.withDayOfMonth(1).dayOfWeek.value // 1 = Monday, 7 = Sunday
                val leadingEmptyDays = firstDayOfWeek - 1

                val totalCells = leadingEmptyDays + daysInMonth
                val totalRows = (totalCells + 6) / 7

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (row in 0 until totalRows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            for (col in 0..6) {
                                val cellIndex = row * 7 + col
                                val dayNum = cellIndex - leadingEmptyDays + 1
                                if (cellIndex in leadingEmptyDays until totalCells && dayNum in 1..daysInMonth) {
                                    val cellDate = displayedMonth.withDayOfMonth(dayNum)
                                    val isAllowed = cellDate in minDate..maxDate
                                    val isSelected = cellDate == tempSelectedDate
                                    val isToday = cellDate == todayDate

                                    Surface(
                                        onClick = {
                                            if (isAllowed) {
                                                tempSelectedDate = cellDate
                                            }
                                        },
                                        enabled = isAllowed,
                                        shape = RoundedCornerShape(8.dp),
                                        color = when {
                                            isSelected -> PosColors.Primary
                                            isToday -> PosColors.PrimaryLight
                                            else -> Color.Transparent
                                        },
                                        border = when {
                                            isSelected -> null
                                            isToday -> BorderStroke(1.dp, PosColors.Primary)
                                            else -> null
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                            .padding(horizontal = 2.dp)
                                            .pointerHoverIcon(if (isAllowed) PointerIcon.Hand else PointerIcon.Default)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Text(
                                                dayNum.toString(),
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                                                color = when {
                                                    isSelected -> Color.White
                                                    !isAllowed -> PosColors.TextLow
                                                    isToday -> PosColors.Primary
                                                    else -> PosColors.TextHigh
                                                }
                                            )
                                        }
                                    }
                                } else {
                                    Spacer(modifier = Modifier.weight(1f).height(38.dp))
                                }
                            }
                        }
                    }
                }

                // Quick jump to Today
                if (canSelectToday) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(
                            onClick = {
                                tempSelectedDate = todayDate
                                displayedMonth = todayDate.withDayOfMonth(1)
                            }
                        ) {
                            Text(
                                "📅 " + strings.text("Aller à Aujourd'hui", "Go to Today", "الانتقال إلى اليوم"),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PosColors.Primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDateSelected(tempSelectedDate)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = PosColors.Primary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(strings.confirm, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(strings.cancel)
            }
        }
    )
}
