package com.example.foodrescue.volunteer.web;

import com.example.foodrescue.common.BusinessException;
import com.example.foodrescue.volunteer.VolunteerService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/reservations")
@PreAuthorize("hasRole('VOLUNTEER')")
public class ReservationWebController {

    private final VolunteerService volunteerService;

    public ReservationWebController(VolunteerService volunteerService) {
        this.volunteerService = volunteerService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("form", new ReservationForm(null));
        addPageData(model);
        return "volunteer/reservations";
    }

    @PostMapping
    public String reserve(
            @Valid @ModelAttribute("form") ReservationForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            addPageData(model);
            return "volunteer/reservations";
        }
        try {
            volunteerService.reserve(form.lotId());
        } catch (BusinessException ex) {
            bindingResult.reject("business", ex.getMessage());
            addPageData(model);
            return "volunteer/reservations";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Лот зарезервовано.");
        return "redirect:/reservations";
    }

    private void addPageData(Model model) {
        model.addAttribute("reservations", volunteerService.getReservations());
        model.addAttribute("lots", volunteerService.getAvailableLots());
    }
}
