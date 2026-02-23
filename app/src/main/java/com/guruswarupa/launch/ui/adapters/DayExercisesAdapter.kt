package com.guruswarupa.launch.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.guruswarupa.launch.models.WorkoutExercise
import com.guruswarupa.launch.R
import com.guruswarupa.launch.models.ExerciseType
class DayExercisesAdapter(
    private val exercises: List<Pair<WorkoutExercise, Int>>
) : RecyclerView.Adapter<DayExercisesAdapter.DayExerciseViewHolder>() {
    
    class DayExerciseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val exerciseName: TextView = itemView.findViewById(R.id.day_exercise_name)
        val exerciseCount: TextView = itemView.findViewById(R.id.day_exercise_count)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayExerciseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.workout_day_exercise_item, parent, false)
        return DayExerciseViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: DayExerciseViewHolder, position: Int) {
        val (exercise, count) = exercises[position]
        
        holder.exerciseName.text = exercise.name
        holder.exerciseCount.text = if (exercise.type == ExerciseType.TIME) {
            exercise.formatTime(count)
        } else {
            count.toString()
        }
    }
    
    override fun getItemCount() = exercises.size
}
