package com.example.foodrescue.delivery.web;

import com.example.foodrescue.common.BusinessException;
import com.example.foodrescue.common.FoodCategory;
import com.example.foodrescue.delivery.DeliveryService;
import com.example.foodrescue.delivery.DestinationPointRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Set;

@Controller
@RequestMapping("/destination-points")
@PreAuthorize("hasRole('COORDINATOR')")
public class DestinationPointWebController {
    private static final String EDITING = "editing";
    private static final String FORM_VIEW = "delivery/create-form";
    private static final String SUCCESS_MESSAGE = "successMessage";
    private static final String REDIRECT_LIST = "redirect:/destination-points";

    private final DeliveryService deliveryService;

    public DestinationPointWebController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("points", deliveryService.getDestinationPoints());
        return "delivery/list";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("form", new CreateDestinationPointForm(null, "", "", "", Set.of()));
        addOptions(model);
        model.addAttribute(EDITING, false);
        return FORM_VIEW;
    }

    @PostMapping
    public String create(
            @Valid @ModelAttribute("form") CreateDestinationPointForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            addOptions(model);
            model.addAttribute(EDITING, false);
            return FORM_VIEW;
        }

        try {
            deliveryService.createDestinationPoint(new DestinationPointRequest(
                    form.organizationId(),
                    form.name(),
                    form.address(),
                    form.workingHours(),
                    form.acceptedCategories()));
        } catch (BusinessException ex) {
            bindingResult.reject("business", ex.getMessage());
            addOptions(model);
            model.addAttribute(EDITING, false);
            return FORM_VIEW;
        }

        redirectAttributes.addFlashAttribute(SUCCESS_MESSAGE, "Пункт призначення успішно створено.");
        return REDIRECT_LIST;
    }

    @GetMapping("/{id}/edit")
    public String showEditForm(@PathVariable java.util.UUID id, Model model) {
        var point = deliveryService.getDestinationPoint(id);
        model.addAttribute("form", new CreateDestinationPointForm(
                point.organizationId(), point.name(), point.address(),
                point.workingHours(), point.acceptedCategories()));
        addOptions(model);
        model.addAttribute(EDITING, true);
        model.addAttribute("pointId", id);
        return FORM_VIEW;
    }

    @PostMapping("/{id}")
    public String update(
            @PathVariable java.util.UUID id,
            @Valid @ModelAttribute("form") CreateDestinationPointForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (!bindingResult.hasErrors()) {
            try {
                deliveryService.updateDestinationPoint(id, new DestinationPointRequest(
                        form.organizationId(), form.name(), form.address(),
                        form.workingHours(), form.acceptedCategories()));
            } catch (BusinessException ex) {
                bindingResult.reject("business", ex.getMessage());
            }
        }
        if (bindingResult.hasErrors()) {
            addOptions(model);
            model.addAttribute(EDITING, true);
            model.addAttribute("pointId", id);
            return FORM_VIEW;
        }
        redirectAttributes.addFlashAttribute(SUCCESS_MESSAGE, "Пункт призначення оновлено.");
        return REDIRECT_LIST;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable java.util.UUID id, RedirectAttributes redirectAttributes) {
        try {
            deliveryService.deleteDestinationPoint(id);
            redirectAttributes.addFlashAttribute(SUCCESS_MESSAGE, "Пункт призначення видалено.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return REDIRECT_LIST;
    }

    private void addOptions(Model model) {
        model.addAttribute("categories", FoodCategory.values());
    }
}
