import { WeeklyScheduleModel } from "../model/media";

export default function calculateWeeklyImpressions(dailyImpressions: number, weeklySchedule: WeeklyScheduleModel[]){
    if (!weeklySchedule || weeklySchedule.length === 0) {
        return 0
    }
    const activeDayCount = weeklySchedule.filter((s) => s.isActive).length;
    
    return(dailyImpressions * activeDayCount);
}